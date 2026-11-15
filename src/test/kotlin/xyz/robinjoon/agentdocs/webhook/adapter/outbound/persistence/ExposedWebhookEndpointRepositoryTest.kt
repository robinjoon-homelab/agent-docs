package xyz.robinjoon.agentdocs.webhook.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.Subscription
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookUrl
import java.time.Instant
import java.util.UUID

class ExposedWebhookEndpointRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedWebhookEndpointRepository()
    private val at = Instant.parse("2026-11-16T03:00:00.123456Z")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `an endpoint round trips with its secret, events, and timeline`() {
        val endpoint = endpoint(at, setOf(WebhookEventType.COMMENT_MENTIONED, WebhookEventType.DOCUMENT_CHANGED))
        val rotated = endpoint.rotateSecret(WebhookSecret.of(ByteArray(64) { 3 }), at.plusSeconds(10))
        val stopped = rotated.stop(StopReason.GONE, at.plusSeconds(20))

        database.inTransaction { repository.add(endpoint) }
        val added = database.inTransaction { repository.findById(endpoint.id) }
        database.inTransaction { repository.update(stopped) }
        val updated = database.inTransaction { repository.findByIdForUpdate(endpoint.id) }

        assertThat(added).isEqualTo(endpoint)
        assertThat(updated).isEqualTo(stopped)
        assertThat(database.inTransaction { repository.findById(WebhookEndpointId(UUID.randomUUID())) }).isNull()
    }

    @Test
    fun `the list keeps stopped endpoints newest first while only active ones are locked for planning`() {
        val older = endpoint(at, setOf(WebhookEventType.DOCUMENT_CHANGED))
        val newer = endpoint(at.plusSeconds(60), setOf(WebhookEventType.COMMENT_CREATED))
        val stopped = endpoint(at.plusSeconds(120), setOf(WebhookEventType.COMMENT_CREATED)).stop(StopReason.OWNER, at.plusSeconds(130))
        database.inTransaction { listOf(older, newer, stopped).forEach(repository::add) }

        val listed = database.inTransaction { repository.list() }
        val active = database.inTransaction { repository.lockActive() }

        assertThat(listed.map { it.id }).containsExactly(stopped.id, newer.id, older.id)
        assertThat(active.map { it.id }).containsExactlyInAnyOrder(older.id, newer.id)
    }

    private fun endpoint(
        createdAt: Instant,
        events: Set<WebhookEventType>,
    ): WebhookEndpoint =
        WebhookEndpoint.register(
            WebhookEndpointId(UUID.randomUUID()),
            Subscription.of(WebhookUrl.of("http://192.168.0.20:8080/hook?team=1"), events),
            WebhookSecret.of(ByteArray(32) { it.toByte() }),
            createdAt,
        )
}
