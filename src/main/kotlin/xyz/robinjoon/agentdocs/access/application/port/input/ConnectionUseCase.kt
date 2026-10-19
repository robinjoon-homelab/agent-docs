package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.GrantChange
import xyz.robinjoon.agentdocs.access.application.model.IssuedToken
import xyz.robinjoon.agentdocs.access.application.model.ScriptTokenRequest
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId

// Owner only; anyone else fails with OwnerOnlyException.
interface ConnectionUseCase {
    // Newest first, revoked ones included.
    fun list(caller: Caller): List<Connection>

    fun issue(
        caller: Caller,
        request: ScriptTokenRequest,
    ): IssuedToken

    // Takes effect from the connection's next request.
    fun regrant(
        caller: Caller,
        id: ConnectionId,
        change: GrantChange,
    ): Connection

    fun revoke(
        caller: Caller,
        id: ConnectionId,
    ): Connection
}
