package xyz.robinjoon.agentdocs.webhook.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityScan
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.webhook.application.model.SweepPage
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookSettings
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDeliveryId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import java.time.Clock
import java.time.Instant
import java.util.UUID

// Turns one page of the activity log into deliveries, each page in its own transaction. Active endpoints are locked
// first, so two passes run one after the other and the second sees what the first added instead of inserting a key
// twice. The page is read again on every pass; only the unique key decides what is new.
@Transactional(readOnly = true)
class WebhookDeliveryPlanner(
    private val endpoints: WebhookEndpointRepository,
    private val deliveries: WebhookDeliveryRepository,
    private val catalog: ActivityCatalog,
    private val settings: WebhookSettings,
    private val clock: Clock,
) {
    // from is the earliest event time this pass looks at; null reaches back to the oldest active endpoint's start.
    @Transactional
    fun planPage(
        from: Instant?,
        afterId: Long?,
    ): SweepPage {
        val active = endpoints.lockActive()
        if (active.isEmpty()) return SweepPage(emptyList(), null)
        val start = active.minOf { it.timeline.createdAt }
        val since = if (from != null && from.isAfter(start)) from else start
        val page = catalog.scan(ActivityScan(since, setOf(WebhookEvents.REVISION_CONFIRMED), true, afterId, PAGE_SIZE))
        val planned = missing(active, page)
        if (planned.isNotEmpty()) deliveries.addAll(planned)
        return SweepPage(planned, page.lastOrNull()?.id?.takeIf { page.size == PAGE_SIZE })
    }

    private fun missing(
        active: List<WebhookEndpoint>,
        page: List<ActivityEntry>,
    ): List<WebhookDelivery> {
        val events = page.flatMap { WebhookEvents.of(it, settings.publicUrl) }
        val now = Instant.now(clock)
        val wanted =
            active.flatMap { endpoint ->
                events.filter { endpoint.wants(it.type, it.occurredAt) }.map { WebhookDelivery.plan(newId(), endpoint.id, it, now) }
            }
        if (wanted.isEmpty()) return emptyList()
        val known = deliveries.keysAmong(active.map { it.id }.toSet(), page.first().id..page.last().id)
        return wanted.filterNot { it.key in known }
    }

    private fun newId(): WebhookDeliveryId = WebhookDeliveryId(UUID.randomUUID())

    private companion object {
        const val PAGE_SIZE = 500
    }
}
