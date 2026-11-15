package xyz.robinjoon.agentdocs.webhook.domain.endpoint

class EndpointStoppedException(
    id: WebhookEndpointId,
) : IllegalStateException("webhook endpoint $id is stopped; register a new endpoint instead")
