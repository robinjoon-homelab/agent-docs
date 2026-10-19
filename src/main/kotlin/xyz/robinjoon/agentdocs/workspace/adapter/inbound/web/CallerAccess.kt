package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.security.core.Authentication
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import java.security.Principal

// Turns the caller the access context authenticated into what workspace use cases decide on. A person's browser
// writes come from the web; a token without MCP is the REST API.
object CallerAccess {
    fun of(principal: Principal?): Access {
        val caller = (principal as? Authentication)?.principal as? Caller ?: throw UnauthenticatedException()
        val agent = caller.agent
        val actor =
            if (agent ==
                null
            ) {
                Actor.human(caller.userId, caller.login)
            } else {
                Actor.agent(caller.userId, caller.login, AgentIdentity(agent.connectionId, agent.clientName))
            }
        val spaces = caller.spaces?.let { ids -> SpaceScope.Listed(ids.map(::SpaceId).toSet()) } ?: SpaceScope.AllSpaces
        return Access(actor, spaces, permissionOf(caller.scope))
    }

    fun write(
        principal: Principal?,
        operationId: String?,
        summary: String?,
    ): WriteContext {
        val access = of(principal)
        return WriteContext(access, if (access.actor.isAgent) ChangeSource.REST else ChangeSource.WEB, operationId, summary)
    }

    private fun permissionOf(scope: CallerScope): Permission =
        when (scope) {
            CallerScope.READ -> Permission.READ
            CallerScope.COMMENT -> Permission.COMMENT
            CallerScope.EDIT -> Permission.EDIT
            CallerScope.OWNER -> Permission.OWNER
        }
}
