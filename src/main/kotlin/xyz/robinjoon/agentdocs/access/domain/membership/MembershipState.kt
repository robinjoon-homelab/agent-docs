package xyz.robinjoon.agentdocs.access.domain.membership

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Instant

// The parts of a membership that change: its role, the user it was fixed to at that person's first sign-in, and when
// it was revoked.
data class MembershipState(
    val role: SpaceRole,
    val member: UserId?,
    val joinedAt: Instant?,
    val revokedAt: Instant?,
) {
    init {
        if ((member == null) != (joinedAt == null)) {
            throw InvalidAccessException("a membership is fixed to a user exactly when it was joined")
        }
    }
}
