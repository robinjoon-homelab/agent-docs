package xyz.robinjoon.agentdocs.webhook.domain.delivery

import java.time.Instant

// Where a delivery stands. A pending delivery always has a next attempt time; a finished one never does. attempts
// counts requests already taken, including one in flight.
data class DeliveryProgress(
    val state: DeliveryState,
    val attempts: Int,
    val nextAttemptAt: Instant?,
    val last: DeliveryAttempt?,
) {
    init {
        require((state == DeliveryState.PENDING) == (nextAttemptAt != null)) { "only a pending delivery has a next attempt" }
        require(attempts in 0..RetrySchedule.ATTEMPTS) { "a delivery makes at most ${RetrySchedule.ATTEMPTS} attempts" }
    }
}

enum class DeliveryState {
    PENDING,
    SUCCEEDED,
    FAILED,
}

// The last request: its answer status, or a short error code when there was no answer.
data class DeliveryAttempt(
    val at: Instant,
    val status: Int?,
    val error: String?,
) {
    companion object {
        fun of(
            result: AttemptResult,
            at: Instant,
        ): DeliveryAttempt =
            when (result) {
                is AttemptResult.Answered -> DeliveryAttempt(at, result.status, null)
                is AttemptResult.Unanswered -> DeliveryAttempt(at, null, result.error)
            }
    }
}
