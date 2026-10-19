package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity

// The five columns that store an actor, named <prefix>_kind, _user_id, _login, _connection_id, _client_name.
internal class ActorColumns(
    table: Table,
    prefix: String,
) {
    val kind = table.enumerationByName("${prefix}_kind", KIND_LENGTH, ActorKind::class)
    val userId = table.javaUUID("${prefix}_user_id")
    val login = table.varchar("${prefix}_login", LOGIN_LENGTH)
    val connectionId = with(table) { javaUUID("${prefix}_connection_id").nullable() }
    val clientName = with(table) { varchar("${prefix}_client_name", CLIENT_NAME_LENGTH).nullable() }
    val columns: List<Column<*>> = listOf(kind, userId, login, connectionId, clientName)

    fun write(
        statement: UpdateBuilder<*>,
        actor: Actor,
    ) {
        statement[kind] = actor.kind
        statement[userId] = actor.userId
        statement[login] = actor.login
        statement[connectionId] = actor.agent?.connectionId
        statement[clientName] = actor.agent?.clientName
    }

    fun read(row: ResultRow): Actor {
        val connection = row[connectionId]
        val agent = connection?.let { AgentIdentity(it, row[clientName].orEmpty()) }
        return Actor(row[kind], row[userId], row[login], agent)
    }

    companion object {
        const val KIND_LENGTH = 10
        const val LOGIN_LENGTH = 39
        const val CLIENT_NAME_LENGTH = 200
    }
}
