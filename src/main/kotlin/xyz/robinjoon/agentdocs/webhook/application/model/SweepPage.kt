package xyz.robinjoon.agentdocs.webhook.application.model

import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery

// The deliveries one page of the log added, and the id to continue after, or null at the end of the pass.
data class SweepPage(
    val planned: List<WebhookDelivery>,
    val next: Long?,
)
