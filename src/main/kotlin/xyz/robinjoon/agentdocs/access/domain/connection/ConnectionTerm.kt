package xyz.robinjoon.agentdocs.access.domain.connection

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import java.time.Duration
import java.time.Instant

data class ConnectionTerm(
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val lastUsedAt: Instant?,
) {
    val lifetime: Duration get() = Duration.between(createdAt, expiresAt)

    companion object {
        const val MAX_LIFETIME_DAYS = 365
        val shortestLifetime: Duration = Duration.ofDays(1)
        val longestLifetime: Duration = Duration.ofDays(MAX_LIFETIME_DAYS.toLong())

        // The lifetime the owner picked in days, checked when it is chosen so an approval cannot hold a bad one.
        fun lifetimeOf(days: Int): Duration {
            if (days !in 1..MAX_LIFETIME_DAYS) throw InvalidAccessException("a connection lasts 1 to $MAX_LIFETIME_DAYS days")
            return Duration.ofDays(days.toLong())
        }
    }
}
