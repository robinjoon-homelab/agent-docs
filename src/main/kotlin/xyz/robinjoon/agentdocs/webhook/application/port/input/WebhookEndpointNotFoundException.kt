package xyz.robinjoon.agentdocs.webhook.application.port.input

import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId

class WebhookEndpointNotFoundException(
    id: WebhookEndpointId,
) : RuntimeException("webhook endpoint $id was not found")
