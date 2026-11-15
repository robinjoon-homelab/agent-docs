package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import xyz.robinjoon.agentdocs.application.model.Credential
import xyz.robinjoon.agentdocs.application.model.CurrentCallerSnapshot
import xyz.robinjoon.agentdocs.application.port.input.CurrentCallerUseCase
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope

class DocumentEventCaller(
    private val callers: CurrentCallerUseCase,
) {
    fun credential(request: HttpServletRequest): Credential {
        val header = request.getHeader("Authorization")
        if (header != null) {
            if (!header.startsWith("Bearer ", ignoreCase = true)) throw UnauthenticatedException()
            return Credential(Credential.Kind.BEARER, header.substring(7))
        }
        val cookie =
            request.cookies?.firstOrNull { it.name == "agent_docs_session" }?.value
                ?: throw UnauthenticatedException()
        return Credential(Credential.Kind.WEB_SESSION, cookie)
    }

    fun current(credential: Credential): Access {
        val caller = callers.current(credential) ?: throw UnauthenticatedException()
        val connection = caller.connection
        val actor =
            if (connection == null) {
                Actor.human(caller.userId, caller.login)
            } else {
                Actor.agent(caller.userId, caller.login, AgentIdentity(connection.id, connection.name))
            }
        val spaces = caller.spaces?.let { SpaceScope.Listed(it.map(::SpaceId).toSet()) } ?: SpaceScope.AllSpaces
        return Access(actor, spaces, permission(caller.scope))
    }

    private fun permission(scope: CurrentCallerSnapshot.Scope): Permission = Permission.valueOf(scope.name)
}
