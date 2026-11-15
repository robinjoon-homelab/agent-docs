package xyz.robinjoon.agentdocs.webhook.application.model

import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint

// A delivery taken for one attempt and the endpoint to send it to. endpoint is null when taking the attempt ended the
// delivery instead, so there is nothing to send.
data class DeliveryClaim(
    val delivery: WebhookDelivery,
    val endpoint: WebhookEndpoint?,
)
