package xyz.robinjoon.agentdocs.webhook.domain.delivery

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import java.time.Duration
import java.time.Instant

// One event on its way to one endpoint. Its key is unique, so planning the same event again never sends it twice;
// receivers may still see a repeat after a crash mid-request and drop it by webhook-id. Records are kept, not pruned.
@AggregateRoot
@ConsistentCopyVisibility
data class WebhookDelivery private constructor(
    val id: WebhookDeliveryId,
    val endpointId: WebhookEndpointId,
    val event: WebhookEvent,
    val progress: DeliveryProgress,
) {
    val key: DeliveryKey get() = DeliveryKey(endpointId, event.source, event.type, event.mentioned)

    // True for a delivery claimed for a request; false when claiming ended it.
    val inFlight: Boolean get() = progress.state == DeliveryState.PENDING

    fun isDue(at: Instant): Boolean = progress.state == DeliveryState.PENDING && progress.nextAttemptAt?.isAfter(at) == false

    // The attempt is counted before the request goes out and held for the lease, so a crash mid-request sends it again
    // once the lease ends instead of losing it, and the attempt limit still holds. When the lease of the last attempt
    // ends without an answer recorded, that attempt failed and the delivery ends instead of being sent a ninth time.
    fun claim(at: Instant): WebhookDelivery {
        check(isDue(at)) { "delivery $id is not due" }
        if (progress.attempts >= RetrySchedule.ATTEMPTS) {
            val unanswered = DeliveryAttempt(at, null, UNRECORDED)
            return copy(progress = DeliveryProgress(DeliveryState.FAILED, progress.attempts, null, unanswered))
        }
        return copy(progress = progress.copy(attempts = progress.attempts + 1, nextAttemptAt = at.plus(lease)))
    }

    // null when the attempt is no longer the current one: the delivery finished or its lease ended and another attempt
    // took over.
    fun record(
        attempt: Int,
        result: AttemptResult,
        at: Instant,
    ): WebhookDelivery? {
        if (progress.state != DeliveryState.PENDING || progress.attempts != attempt) return null
        val last = DeliveryAttempt.of(result, at)
        val next =
            when {
                result.succeeded -> DeliveryProgress(DeliveryState.SUCCEEDED, attempt, null, last)
                result.gone || attempt >= RetrySchedule.ATTEMPTS -> DeliveryProgress(DeliveryState.FAILED, attempt, null, last)
                else -> DeliveryProgress(DeliveryState.PENDING, attempt, RetrySchedule.next(attempt, at, retryAfterOf(result)), last)
            }
        return copy(progress = next)
    }

    private fun retryAfterOf(result: AttemptResult): Duration? = (result as? AttemptResult.Answered)?.retryAfter

    companion object {
        // Longer than the 15 second request limit, so an attempt in flight is not taken twice.
        val lease: Duration = Duration.ofMinutes(2)
        const val UNRECORDED = "no_answer_recorded"

        fun plan(
            id: WebhookDeliveryId,
            endpointId: WebhookEndpointId,
            event: WebhookEvent,
            at: Instant,
        ): WebhookDelivery = WebhookDelivery(id, endpointId, event, DeliveryProgress(DeliveryState.PENDING, 0, at, null))

        fun restore(
            id: WebhookDeliveryId,
            endpointId: WebhookEndpointId,
            event: WebhookEvent,
            progress: DeliveryProgress,
        ): WebhookDelivery = WebhookDelivery(id, endpointId, event, progress)
    }
}

// What makes a delivery unique: an endpoint gets an activity's event once, and a mention event once per login.
data class DeliveryKey(
    val endpointId: WebhookEndpointId,
    val source: Long,
    val type: WebhookEventType,
    val mentioned: String?,
)
