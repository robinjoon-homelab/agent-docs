package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus

internal object DocumentsTable : Table("documents") {
    val id = javaUUID("id")
    val spaceId = javaUUID("space_id")
    val path = varchar("doc_path", 300)
    val activePath = varchar("active_path", 300).nullable()
    val title = varchar("title", 200)
    val status = enumerationByName("status", 20, DocumentStatus::class)
    val tags = varchar("tags", 1000)
    val assignee = varchar("assignee", 39).nullable()
    val body = text("body")
    val searchText = text("search_text")
    val revision = integer("revision")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val trashedAt = timestampWithTimeZone("trashed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
