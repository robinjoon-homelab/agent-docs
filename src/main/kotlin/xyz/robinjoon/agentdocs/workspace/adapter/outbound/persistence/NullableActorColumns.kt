package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity

// ActorColumns for an actor that may be absent, such as who resolved a thread.
internal class NullableActorColumns(
    table: Table,
    prefix: String,
) {
    val kind = with(table) { enumerationByName("${prefix}_kind", ActorColumns.KIND_LENGTH, ActorKind::class).nullable() }
    val userId = with(table) { javaUUID("${prefix}_user_id").nullable() }
    val login = with(table) { varchar("${prefix}_login", ActorColumns.LOGIN_LENGTH).nullable() }
    val connectionId = with(table) { javaUUID("${prefix}_connection_id").nullable() }
    val clientName = with(table) { varchar("${prefix}_client_name", ActorColumns.CLIENT_NAME_LENGTH).nullable() }

    fun write(
        statement: UpdateBuilder<*>,
        actor: Actor?,
    ) {
        statement[kind] = actor?.kind
        statement[userId] = actor?.userId
        statement[login] = actor?.login
        statement[connectionId] = actor?.agent?.connectionId
        statement[clientName] = actor?.agent?.clientName
    }

    fun read(row: ResultRow): Actor? {
        val actorKind = row[kind] ?: return null
        val agent = row[connectionId]?.let { AgentIdentity(it, row[clientName].orEmpty()) }
        return Actor(actorKind, requireNotNull(row[userId]), requireNotNull(row[login]), agent)
    }
}
