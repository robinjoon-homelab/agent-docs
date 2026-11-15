package xyz.robinjoon.agentdocs.webhook.application.port.input

import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery

// Plans deliveries from the activity log. Both passes return the deliveries they added; planning the same entry
// again adds nothing.
interface WebhookSweepUseCase {
    // The last few minutes of the log, often, so events go out soon.
    fun sweepRecent(): List<WebhookDelivery>

    // Everything the log still keeps since the oldest active endpoint started, so an entry that committed late under a
    // smaller id than ones already read is planned on the next pass instead of being skipped for good.
    fun reconcile(): List<WebhookDelivery>
}
