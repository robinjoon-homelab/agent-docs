package xyz.robinjoon.agentdocs.webhook.adapter.inbound.web

import xyz.robinjoon.agentdocs.webhook.application.model.IssuedEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.delivery.RetrySchedule
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import java.time.Instant
import java.util.UUID

data class RegisterWebhookRequest(
    val url: String,
    val events: List<String>,
)

// Never carries the signing secret.
class WebhookEndpointResponse private constructor(
    endpoint: WebhookEndpoint,
) {
    val id: UUID = endpoint.id.value
    val url: String = endpoint.subscription.url.toString()
    val events: List<String> =
        endpoint.subscription.events
            .map { it.wire }
            .sorted()
    val active: Boolean = endpoint.active
    val createdAt: Instant = endpoint.timeline.createdAt
    val secretRotatedAt: Instant? = endpoint.timeline.secretRotatedAt
    val stoppedAt: Instant? = endpoint.timeline.stop?.at
    val stopReason: String? =
        endpoint.timeline.stop
            ?.reason
            ?.name
            ?.lowercase()

    companion object {
        fun from(endpoint: WebhookEndpoint): WebhookEndpointResponse = WebhookEndpointResponse(endpoint)
    }
}

// The only response with the secret: right after the endpoint was created or its secret replaced.
data class IssuedWebhookResponse(
    val endpoint: WebhookEndpointResponse,
    val secret: String,
) {
    override fun toString(): String = "IssuedWebhookResponse(${endpoint.id})"

    companion object {
        fun from(issued: IssuedEndpoint): IssuedWebhookResponse =
            IssuedWebhookResponse(WebhookEndpointResponse.from(issued.endpoint), issued.secret)
    }
}

class WebhookDeliveryResponse private constructor(
    delivery: WebhookDelivery,
) {
    val id: UUID = delivery.id.value
    val webhookId: String = delivery.id.messageId
    val event: String = delivery.event.type.wire
    val activityId: Long = delivery.event.source
    val mentioned: String? = delivery.event.mentioned
    val occurredAt: Instant = delivery.event.occurredAt
    val state: String =
        delivery.progress.state.name
            .lowercase()
    val attempts: Int = delivery.progress.attempts
    val maxAttempts: Int = RetrySchedule.ATTEMPTS
    val nextAttemptAt: Instant? = delivery.progress.nextAttemptAt
    val lastAttemptAt: Instant? = delivery.progress.last?.at
    val lastStatus: Int? = delivery.progress.last?.status
    val lastError: String? = delivery.progress.last?.error

    companion object {
        fun from(delivery: WebhookDelivery): WebhookDeliveryResponse = WebhookDeliveryResponse(delivery)
    }
}
