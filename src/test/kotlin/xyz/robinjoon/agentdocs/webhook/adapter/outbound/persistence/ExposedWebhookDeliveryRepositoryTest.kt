package xyz.robinjoon.agentdocs.webhook.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryKey
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventActor
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventFacts
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventSubject
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDeliveryId
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookEvent
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.Subscription
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookUrl
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class ExposedWebhookDeliveryRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedWebhookDeliveryRepository()
    private val endpoints = ExposedWebhookEndpointRepository()
    private val at = Instant.parse("2026-11-16T03:00:00.123456Z")
    private val endpoint = endpoint()

    init {
        database.inTransaction { endpoints.add(endpoint) }
    }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `planned deliveries round trip and their keys are found by endpoint and source range`() {
        val changed = delivery(endpoint, 10, WebhookEventType.DOCUMENT_CHANGED, null)
        val mentioned = delivery(endpoint, 11, WebhookEventType.COMMENT_MENTIONED, "alice")
        database.inTransaction { repository.addAll(listOf(changed, mentioned)) }

        val keys = database.inTransaction { repository.keysAmong(setOf(endpoint.id), 10L..11L) }
        val outside = database.inTransaction { repository.keysAmong(setOf(endpoint.id), 12L..20L) }
        val read = database.inTransaction { repository.findByIdForUpdate(mentioned.id) }

        assertThat(keys).containsExactlyInAnyOrder(changed.key, mentioned.key)
        assertThat(keys).contains(DeliveryKey(endpoint.id, 10, WebhookEventType.DOCUMENT_CHANGED, null))
        assertThat(outside).isEmpty()
        assertThat(read).isEqualTo(mentioned)
    }

    @Test
    fun `the same event cannot be planned twice for one endpoint`() {
        database.inTransaction { repository.addAll(listOf(delivery(endpoint, 10, WebhookEventType.COMMENT_MENTIONED, "alice"))) }

        assertThatThrownBy {
            database.inTransaction { repository.addAll(listOf(delivery(endpoint, 10, WebhookEventType.COMMENT_MENTIONED, "alice"))) }
        }.isInstanceOf(SQLException::class.java)
        database.inTransaction { repository.addAll(listOf(delivery(endpoint, 10, WebhookEventType.COMMENT_MENTIONED, "bob"))) }
        assertThat(database.inTransaction { repository.keysAmong(setOf(endpoint.id), 10L..10L) }).hasSize(2)
    }

    @Test
    fun `the next due delivery is the one waiting longest on an endpoint that is not stopped`() {
        val stoppedEndpoint = endpoint().stop(StopReason.OWNER, at)
        database.inTransaction { endpoints.add(stoppedEndpoint) }
        val first = due(endpoint, 1, at)
        val second = due(endpoint, 2, at.plusSeconds(1))
        val onStopped = due(stoppedEndpoint, 3, at.minusSeconds(60))
        database.inTransaction { repository.addAll(listOf(second, first, onStopped)) }

        val next = database.inTransaction { repository.lockNextDue(at.plusSeconds(1)) }
        database.inTransaction { repository.update(first.claim(at.plusSeconds(1))) }
        val afterClaim = database.inTransaction { repository.lockNextDue(at.plusSeconds(1)) }
        val early = database.inTransaction { repository.lockNextDue(at.minusSeconds(1)) }

        assertThat(next?.id).isEqualTo(first.id)
        assertThat(afterClaim?.id).isEqualTo(second.id)
        assertThat(early).isNull()
    }

    @Test
    fun `progress updates keep the event and recent deliveries come newest event first`() {
        val older = delivery(endpoint, 1, WebhookEventType.DOCUMENT_CHANGED, null)
        val newer = delivery(endpoint, 2, WebhookEventType.DOCUMENT_CHANGED, null)
        database.inTransaction { repository.addAll(listOf(older, newer)) }
        val claimed = older.claim(at)
        val failed = requireNotNull(claimed.record(1, AttemptResult.Answered(503, null), at.plusSeconds(2)))

        database.inTransaction { repository.update(failed) }
        val recent = database.inTransaction { repository.recent(endpoint.id, 10) }

        assertThat(recent.map { it.id }).containsExactly(newer.id, older.id)
        assertThat(recent.last()).isEqualTo(failed)
        assertThat(database.inTransaction { repository.recent(endpoint.id, 1) }).hasSize(1)
    }

    private fun endpoint(): WebhookEndpoint =
        WebhookEndpoint.register(
            WebhookEndpointId(UUID.randomUUID()),
            Subscription.of(WebhookUrl.of("https://hooks.example.com/hook"), WebhookEventType.entries.toSet()),
            WebhookSecret.of(ByteArray(32)),
            at,
        )

    private fun delivery(
        endpoint: WebhookEndpoint,
        source: Long,
        type: WebhookEventType,
        mentioned: String?,
    ): WebhookDelivery = WebhookDelivery.plan(WebhookDeliveryId(UUID.randomUUID()), endpoint.id, event(source, type, mentioned), at)

    private fun due(
        endpoint: WebhookEndpoint,
        source: Long,
        plannedAt: Instant,
    ): WebhookDelivery {
        val event = event(source, WebhookEventType.DOCUMENT_CHANGED, null)
        return WebhookDelivery.plan(WebhookDeliveryId(UUID.randomUUID()), endpoint.id, event, plannedAt)
    }

    private fun event(
        source: Long,
        type: WebhookEventType,
        mentioned: String?,
    ): WebhookEvent {
        val thread = if (type == WebhookEventType.DOCUMENT_CHANGED) null else UUID.randomUUID()
        val revision = if (type == WebhookEventType.DOCUMENT_CHANGED) 2 else null
        val subject = EventSubject(UUID.randomUUID(), UUID.randomUUID(), thread, null, revision)
        val facts = EventFacts(source, at, subject, EventActor("human", "robinjoon", null))
        return WebhookEvent.of(type, facts, mentioned, "https://docs.example.com")
    }
}
