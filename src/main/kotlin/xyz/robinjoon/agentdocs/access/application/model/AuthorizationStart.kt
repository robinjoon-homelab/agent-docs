package xyz.robinjoon.agentdocs.access.application.model

import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId

// How the authorization endpoint answers. A request with an unknown client or redirect URI cannot be sent back to the
// client, so it is shown as an error page instead (RFC 6749 section 4.1.2.1).
sealed interface AuthorizationStart {
    data class Started(
        val request: AuthorizationRequestId,
    ) : AuthorizationStart

    data class RedirectWithError(
        val location: String,
    ) : AuthorizationStart

    data class Rejected(
        val description: String,
    ) : AuthorizationStart
}
