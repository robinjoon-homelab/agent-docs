package xyz.robinjoon.agentdocs.access.domain.authorization

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Duration

// What the owner approved: the grant and how long the connection lasts, plus the hash of the one-time code that the
// client exchanges for its tokens.
data class Approval(
    val userId: UserId,
    val grant: Grant,
    val lifetime: Duration,
    val codeHash: TokenHash,
)
