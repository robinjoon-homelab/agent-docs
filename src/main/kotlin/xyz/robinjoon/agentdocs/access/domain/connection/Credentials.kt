package xyz.robinjoon.agentdocs.access.domain.connection

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import java.time.Instant

// The current access token and, for an agent, the refresh token that replaces it. Refreshing rotates both, so a
// refresh token works once.
data class Credentials(
    val accessHash: TokenHash,
    val accessExpiresAt: Instant,
    val refreshHash: TokenHash?,
)
