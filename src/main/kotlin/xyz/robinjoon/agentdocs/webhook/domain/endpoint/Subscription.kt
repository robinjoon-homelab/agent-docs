package xyz.robinjoon.agentdocs.webhook.domain.endpoint

import xyz.robinjoon.agentdocs.webhook.domain.InvalidWebhookException
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType

// Where an endpoint's events go and which events it takes. There is no space filter: payloads carry the space id, so a
// receiver filters on its side.
@ConsistentCopyVisibility
data class Subscription private constructor(
    val url: WebhookUrl,
    val events: Set<WebhookEventType>,
) {
    companion object {
        fun of(
            url: WebhookUrl,
            events: Set<WebhookEventType>,
        ): Subscription {
            if (events.isEmpty()) throw InvalidWebhookException("choose at least one event")
            return Subscription(url, events.toSet())
        }
    }
}
