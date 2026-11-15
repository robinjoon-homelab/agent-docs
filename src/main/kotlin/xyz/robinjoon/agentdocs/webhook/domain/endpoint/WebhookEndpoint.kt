package xyz.robinjoon.agentdocs.webhook.domain.endpoint

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import java.time.Instant

// One receiver the owner registered: where events go, which events, and the secret that signs them. It receives only
// events that happened at or after it was created and only while it is not stopped, so stopping is the end of it.
@AggregateRoot
@ConsistentCopyVisibility
data class WebhookEndpoint private constructor(
    val id: WebhookEndpointId,
    val subscription: Subscription,
    val secret: WebhookSecret,
    val timeline: EndpointTimeline,
) {
    val active: Boolean get() = timeline.stop == null

    fun wants(
        type: WebhookEventType,
        occurredAt: Instant,
    ): Boolean = active && type in subscription.events && !occurredAt.isBefore(timeline.createdAt)

    // Stopping twice keeps the first stop, so a 410 after the owner stopped it does not rewrite why it stopped.
    fun stop(
        reason: StopReason,
        at: Instant,
    ): WebhookEndpoint = if (active) copy(timeline = timeline.copy(stop = EndpointStop(at, reason))) else this

    fun rotateSecret(
        secret: WebhookSecret,
        at: Instant,
    ): WebhookEndpoint {
        if (!active) throw EndpointStoppedException(id)
        return copy(secret = secret, timeline = timeline.copy(secretRotatedAt = at))
    }

    companion object {
        fun register(
            id: WebhookEndpointId,
            subscription: Subscription,
            secret: WebhookSecret,
            at: Instant,
        ): WebhookEndpoint = WebhookEndpoint(id, subscription, secret, EndpointTimeline(at, null, null))

        fun restore(
            id: WebhookEndpointId,
            subscription: Subscription,
            secret: WebhookSecret,
            timeline: EndpointTimeline,
        ): WebhookEndpoint = WebhookEndpoint(id, subscription, secret, timeline)
    }
}
