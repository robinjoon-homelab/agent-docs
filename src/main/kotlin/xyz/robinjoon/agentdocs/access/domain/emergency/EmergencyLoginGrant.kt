package xyz.robinjoon.agentdocs.access.domain.emergency

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Duration
import java.time.Instant

@AggregateRoot
@ConsistentCopyVisibility
data class EmergencyLoginGrant private constructor(
    val tokenHash: TokenHash,
    val userId: UserId,
    val expiresAt: Instant,
    val consumed: Boolean,
) {
    fun consume(at: Instant): EmergencyLoginGrant {
        require(!consumed && at < expiresAt) { "Emergency login is unavailable" }
        return copy(consumed = true)
    }

    companion object {
        val lifetime: Duration = Duration.ofMinutes(10)

        fun issue(
            hash: TokenHash,
            userId: UserId,
            at: Instant,
        ): EmergencyLoginGrant = EmergencyLoginGrant(hash, userId, at.plus(lifetime), false)

        fun restore(
            hash: TokenHash,
            userId: UserId,
            expiresAt: Instant,
            consumed: Boolean,
        ): EmergencyLoginGrant = EmergencyLoginGrant(hash, userId, expiresAt, consumed)
    }
}
