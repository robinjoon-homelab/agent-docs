package xyz.robinjoon.agentdocs.access.application.service

import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserRole

internal object Callers {
    // A member signs in but sees no space until invitations exist.
    fun person(
        user: User,
        role: UserRole,
    ): Caller {
        val owner = role == UserRole.OWNER
        return Caller(
            user.id.value,
            user.account.login,
            owner,
            null,
            if (owner) null else emptySet(),
            if (owner) CallerScope.OWNER else CallerScope.READ,
        )
    }

    fun agent(
        connection: Connection,
        login: String,
    ): Caller {
        val scope =
            when (connection.grant.scope) {
                ConnectionScope.READ -> CallerScope.READ
                ConnectionScope.COMMENT -> CallerScope.COMMENT
                ConnectionScope.EDIT -> CallerScope.EDIT
            }
        // Records show the client_id host when there is one: a client's own name is self-declared and anyone can claim it.
        val client = connection.owner.client
        val agent = CallerAgent(connection.id.value, client.host ?: client.name)
        return Caller(connection.owner.userId.value, login, false, agent, connection.grant.spaces, scope)
    }
}
