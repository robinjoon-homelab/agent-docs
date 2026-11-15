package xyz.robinjoon.agentdocs.webhook.domain

// The events an endpoint can subscribe to. wire is the Standard Webhooks type sent in the payload and accepted by the
// API.
enum class WebhookEventType(
    val wire: String,
) {
    DOCUMENT_CHANGED("document.changed"),
    COMMENT_CREATED("comment.created"),
    COMMENT_MENTIONED("comment.mentioned"),
    ;

    companion object {
        fun of(wire: String): WebhookEventType =
            entries.firstOrNull { it.wire == wire }
                ?: throw InvalidWebhookException("event must be one of ${entries.joinToString { it.wire }}")
    }
}
