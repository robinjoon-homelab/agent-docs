package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind

internal object DocumentRevisionsTable : Table("document_revisions") {
    val documentId = javaUUID("document_id")
    val revision = integer("revision")
    val spaceId = javaUUID("space_id")
    val path = varchar("doc_path", 300)
    val title = varchar("title", 200)
    val status = enumerationByName("status", 20, DocumentStatus::class)
    val tags = varchar("tags", 1000)
    val assignee = varchar("assignee", 39).nullable()
    val body = text("body")
    val trashed = bool("trashed")
    val actor = ActorColumns(this, "actor")
    val changeSource = enumerationByName("change_source", 10, ChangeSource::class)
    val summary = varchar("summary", 200)
    val kind = enumerationByName("kind", 10, RevisionKind::class)
    val changedBlocks = text("changed_blocks")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(documentId, revision)
}
