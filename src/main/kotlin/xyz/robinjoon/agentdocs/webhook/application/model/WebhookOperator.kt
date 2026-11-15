package xyz.robinjoon.agentdocs.webhook.application.model

import java.util.UUID

// Who is asking, as the webhook context decides on it. Only the owner signed in as a person manages endpoints; an
// agent never does, even one the owner connected.
data class WebhookOperator(
    val userId: UUID,
    val login: String,
    val owner: Boolean,
    val agent: Boolean,
) {
    val managesWebhooks: Boolean get() = owner && !agent
}
