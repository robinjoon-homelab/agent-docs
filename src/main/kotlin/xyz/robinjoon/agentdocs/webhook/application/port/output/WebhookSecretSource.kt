package xyz.robinjoon.agentdocs.webhook.application.port.output

import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret

interface WebhookSecretSource {
    // A new secret from a cryptographic random source.
    fun generate(): WebhookSecret
}
