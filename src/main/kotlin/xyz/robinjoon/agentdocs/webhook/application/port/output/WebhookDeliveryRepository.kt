package xyz.robinjoon.agentdocs.webhook.application.port.output

import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryKey
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDeliveryId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import java.time.Instant

interface WebhookDeliveryRepository {
    // A delivery whose key already exists fails the insert, so a key is planned once.
    fun addAll(deliveries: List<WebhookDelivery>)

    // The keys already planned for these endpoints from log entries with ids in sources.
    fun keysAmong(
        endpoints: Set<WebhookEndpointId>,
        sources: LongRange,
    ): Set<DeliveryKey>

    // The pending delivery due longest ago whose endpoint is not stopped, locked until the caller's transaction ends.
    fun lockNextDue(at: Instant): WebhookDelivery?

    fun findByIdForUpdate(id: WebhookDeliveryId): WebhookDelivery?

    fun update(delivery: WebhookDelivery)

    // The endpoint's deliveries, newest event first.
    fun recent(
        endpoint: WebhookEndpointId,
        limit: Int,
    ): List<WebhookDelivery>
}
