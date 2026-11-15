package xyz.robinjoon.agentdocs.webhook.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookSettings
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookDeliveryPlanner
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookSweepService
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryKey
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryState
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

class WebhookSweepServiceTest {
    private val endpoints = mockk<WebhookEndpointRepository>()
    private val deliveries = mockk<WebhookDeliveryRepository>()
    private val catalog = mockk<ActivityCatalog>()
    private val now = WebhookFixtures.start.plus(Duration.ofHours(2))
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val planner = WebhookDeliveryPlanner(endpoints, deliveries, catalog, WebhookSettings("https://docs.example.com"), clock)
    private val sweep = WebhookSweepService(planner, clock)

    init {
        every { deliveries.addAll(any()) } just runs
    }

    @Test
    fun `an entry that committed late under a smaller id is planned on the next pass`() {
        val endpoint = WebhookFixtures.endpoint()
        every { endpoints.lockActive() } returns listOf(endpoint)
        val late = WebhookFixtures.confirmed(7, now.minusSeconds(90))
        val seen = WebhookFixtures.confirmed(9, now.minusSeconds(60))
        every { catalog.scan(any()) } returns listOf(late, seen)
        every { deliveries.keysAmong(setOf(endpoint.id), 7L..9L) } returns
            setOf(DeliveryKey(endpoint.id, 9, WebhookEventType.DOCUMENT_CHANGED, null))

        val planned = sweep.sweepRecent()

        assertThat(planned.map { it.event.source }).containsExactly(7)
        assertThat(planned.single().endpointId).isEqualTo(endpoint.id)
        assertThat(planned.single().progress.state).isEqualTo(DeliveryState.PENDING)
        assertThat(planned.single().progress.nextAttemptAt).isEqualTo(now)
    }

    @Test
    fun `a comment fans out to one created event and one event per mentioned login, only as subscribed`() {
        val everything = WebhookFixtures.endpoint()
        val mentionsOnly = WebhookFixtures.endpoint(setOf(WebhookEventType.COMMENT_MENTIONED))
        every { endpoints.lockActive() } returns listOf(everything, mentionsOnly)
        every { catalog.scan(any()) } returns listOf(WebhookFixtures.commented(20, now.minusSeconds(5), setOf("bob", "alice")))
        every { deliveries.keysAmong(any(), 20L..20L) } returns emptySet()

        val planned = sweep.reconcile()

        assertThat(planned.filter { it.endpointId == everything.id }.map { it.event.type to it.event.mentioned }).containsExactly(
            WebhookEventType.COMMENT_CREATED to null,
            WebhookEventType.COMMENT_MENTIONED to "alice",
            WebhookEventType.COMMENT_MENTIONED to "bob",
        )
        assertThat(planned.filter { it.endpointId == mentionsOnly.id }.map { it.event.mentioned }).containsExactly("alice", "bob")
        assertThat(planned.map { it.event.payload }).allSatisfy { assertThat(it).doesNotContain("댓글 본문", "제목") }
    }

    @Test
    fun `entries from before an endpoint started and kinds the webhook does not read are not planned`() {
        val later = WebhookFixtures.endpoint(at = now.minusSeconds(30))
        every { endpoints.lockActive() } returns listOf(later)
        every { catalog.scan(any()) } returns
            listOf(WebhookFixtures.confirmed(30, now.minusSeconds(31)), WebhookFixtures.ignored(31, now.minusSeconds(10)))

        assertThat(sweep.reconcile()).isEmpty()
    }

    @Test
    fun `a full pass continues page by page until a page is not full`() {
        val endpoint = WebhookFixtures.endpoint()
        every { endpoints.lockActive() } returns listOf(endpoint)
        val first = (1L..500L).map { WebhookFixtures.ignored(it, now.minusSeconds(600)) }
        every { catalog.scan(match { it.afterId == null }) } returns first
        every { catalog.scan(match { it.afterId == 500L }) } returns listOf(WebhookFixtures.confirmed(501, now.minusSeconds(1)))
        every { deliveries.keysAmong(setOf(endpoint.id), 501L..501L) } returns emptySet()

        val planned = sweep.reconcile()

        assertThat(planned.map { it.event.source }).containsExactly(501)
    }

    @Test
    fun `with no active endpoint a pass plans nothing`() {
        every { endpoints.lockActive() } returns emptyList()

        assertThat(sweep.sweepRecent()).isEmpty()
        assertThat(sweep.reconcile()).isEmpty()
    }
}
