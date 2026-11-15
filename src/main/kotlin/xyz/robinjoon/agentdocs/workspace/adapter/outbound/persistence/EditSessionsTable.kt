package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason

internal object EditSessionsTable : Table("edit_sessions") {
    val id = javaUUID("id")
    val scope = varchar("scope", 150)
    val documentId = javaUUID("document_id")
    val spaceId = javaUUID("space_id")
    val actor = ActorColumns(this, "actor")
    val tabId = javaUUID("tab_id").nullable()
    val startedAt = timestampWithTimeZone("started_at")
    val lastActivityAt = timestampWithTimeZone("last_activity_at")
    val endedAt = timestampWithTimeZone("ended_at").nullable()
    val reason = enumerationByName("reason", 16, EditEndReason::class).nullable()
    val supersededBy = javaUUID("superseded_by").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object EditSessionScopesTable : Table("edit_session_scopes") {
    val scope = varchar("scope", 150)
    override val primaryKey = PrimaryKey(scope)
}

internal object EditSessionInputsTable : Table("edit_session_inputs") {
    val documentId = javaUUID("document_id")
    val version = integer("version")
    val batchId = javaUUID("batch_id").uniqueIndex()
    override val primaryKey = PrimaryKey(documentId, version)
}

internal object EditSessionOperationsTable : Table("edit_session_operations") {
    val scope = varchar("scope", 150)
    val operation = varchar("operation", 110)
    val sessionId = javaUUID("session_id")
    val startedAt = timestampWithTimeZone("started_at")
    val lastActivityAt = timestampWithTimeZone("last_activity_at")
    val endedAt = timestampWithTimeZone("ended_at").nullable()
    val reason = enumerationByName("reason", 16, EditEndReason::class).nullable()
    override val primaryKey = PrimaryKey(scope, operation)
}
