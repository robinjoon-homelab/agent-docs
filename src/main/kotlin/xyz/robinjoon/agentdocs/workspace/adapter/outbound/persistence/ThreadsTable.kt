package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus

internal object ThreadsTable : Table("threads") {
    val id = javaUUID("id")
    val spaceId = javaUUID("space_id")
    val documentId = javaUUID("document_id")
    val anchorKind = varchar("anchor_kind", 10)
    val anchorBlock = varchar("anchor_block", 16).nullable()
    val anchorRow = varchar("anchor_row", 16).nullable()
    val anchorColumn = varchar("anchor_column", 16).nullable()
    val anchorStart = integer("anchor_start").nullable()
    val anchorEnd = integer("anchor_end").nullable()
    val anchorQuote = varchar("anchor_quote", 2000).nullable()
    val anchoredRevision = integer("anchored_revision")
    val status = enumerationByName("status", 10, ThreadStatus::class)
    val authorKind = enumerationByName("author_kind", 10, ActorKind::class)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val resolvedAt = timestampWithTimeZone("resolved_at").nullable()
    val resolver = NullableActorColumns(this, "resolver")

    override val primaryKey = PrimaryKey(id)
}
