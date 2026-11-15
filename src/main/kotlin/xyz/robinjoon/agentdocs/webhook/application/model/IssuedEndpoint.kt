package xyz.robinjoon.agentdocs.webhook.application.model

import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint

// An endpoint right after it was created or its secret replaced: the only result that shows the signing secret.
class IssuedEndpoint(
    val endpoint: WebhookEndpoint,
) {
    val secret: String get() = endpoint.secret.serialized

    override fun toString(): String = "IssuedEndpoint(${endpoint.id})"
}
