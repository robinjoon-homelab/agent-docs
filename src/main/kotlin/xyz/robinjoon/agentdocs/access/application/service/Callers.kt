package xyz.robinjoon.agentdocs.access.application.service

import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserRole

internal object Callers {
    // A member sees exactly the spaces of its joined memberships, each with its role; a pending invitation grants
    // nothing. The ceiling is the highest role, so it never reaches OWNER. A member of the members setting with no
    // membership sees nothing, as before invitations existed.
    fun person(
        user: User,
        role: UserRole,
        memberships: List<SpaceMembership> = emptyList(),
    ): Caller {
        if (role == UserRole.OWNER) return Caller(user.id.value, user.account.login, true, null, null, CallerScope.OWNER)
        val grants = memberships.filter(SpaceMembership::isActive).associate { it.spaceId to scopeOf(it.role) }
        return Caller(user.id.value, user.account.login, false, null, grants, grants.values.maxOrNull() ?: CallerScope.READ)
    }

    fun agent(
        connection: Connection,
        login: String,
        cliClientId: String? = null,
    ): Caller {
        val scope =
            when (connection.grant.scope) {
                ConnectionScope.READ -> CallerScope.READ
                ConnectionScope.COMMENT -> CallerScope.COMMENT
                ConnectionScope.EDIT -> CallerScope.EDIT
            }
        // A client's own name is self-declared and anyone can claim it, so records show it with the client_id host when
        // there is one: "Claude Code (claude.ai)".
        val client = connection.owner.client
        val label = client.host?.let { "${client.name} ($it)" } ?: client.name
        val agent = CallerAgent(connection.id.value, label.take(MAX_LABEL_LENGTH), cliClientId != null && client.clientId == cliClientId)
        return Caller(connection.owner.userId.value, login, false, agent, connection.grant.spaces.associateWith { scope }, scope)
    }

    private fun scopeOf(role: SpaceRole): CallerScope =
        when (role) {
            SpaceRole.EDITOR -> CallerScope.EDIT
            SpaceRole.COMMENTER -> CallerScope.COMMENT
        }

    // The width of the actor client name columns.
    private const val MAX_LABEL_LENGTH = 200
}
