package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.ApprovalRequest
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationStart
import xyz.robinjoon.agentdocs.access.application.model.AuthorizationView
import xyz.robinjoon.agentdocs.access.application.model.AuthorizeRequest
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId

// The interactive half of the OAuth authorization server: the client starts it, the owner decides on the approval
// screen, and the browser goes back to the client with a code or an error.
interface AuthorizationUseCase {
    // Checks the client, its redirect URI, PKCE S256, and that the resource is this MCP server.
    fun begin(request: AuthorizeRequest): AuthorizationStart

    // Owner only. Fails with AuthorizationRequestNotFoundException for an unknown or expired request.
    fun describe(
        caller: Caller,
        id: AuthorizationRequestId,
    ): AuthorizationView

    // Returns the client redirect with code, state, and iss.
    fun approve(
        caller: Caller,
        id: AuthorizationRequestId,
        approval: ApprovalRequest,
    ): String

    // Returns the client redirect with error=access_denied, state, and iss.
    fun deny(
        caller: Caller,
        id: AuthorizationRequestId,
    ): String
}
