package xyz.robinjoon.agentdocs.access.domain.authorization

import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope

// What the client asked for at the authorization endpoint. resource is the MCP server the token will be for
// (RFC 8707); requestedScope is only a hint, since the owner picks the scope when approving.
data class AuthorizationAsk(
    val clientId: String,
    val redirectUri: String,
    val state: String?,
    val challenge: PkceChallenge,
    val resource: String,
    val requestedScope: ConnectionScope?,
)
