package xyz.robinjoon.agentdocs.webhook.domain.delivery

import java.time.Duration
import java.time.Instant

// Eight attempts at most: right away, then 5 seconds, 30 seconds, 2 minutes, 10 minutes, 1 hour, 6 hours, and 24 hours
// after each failure. A receiver's Retry-After can only push an attempt later, and never past 24 hours.
object RetrySchedule {
    const val ATTEMPTS = 8

    val delays: List<Duration> =
        listOf(
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            Duration.ofHours(6),
            Duration.ofHours(24),
        )

    private val longest: Duration = Duration.ofHours(24)

    // The time of the attempt after the given failed one, counting from 1.
    fun next(
        failed: Int,
        at: Instant,
        retryAfter: Duration?,
    ): Instant {
        require(failed in 1 until ATTEMPTS) { "attempt $failed has no next attempt" }
        val scheduled = delays[failed - 1]
        val asked = retryAfter?.let { minOf(it, longest) }
        return at.plus(if (asked != null && asked > scheduled) asked else scheduled)
    }
}
