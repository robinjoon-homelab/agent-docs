package xyz.robinjoon.agentdocs.webhook.application.port.input

// Webhooks are managed by the owner only, and never by an agent.
class WebhookOwnerOnlyException(
    action: String,
) : RuntimeException("$action is for the owner only")
