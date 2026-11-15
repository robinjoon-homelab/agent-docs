package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object SpacesTable : Table("spaces") {
    val id = javaUUID("id")
    val slug = varchar("slug", 50)
    val name = varchar("space_name", 100)
    val description = varchar("description", 500)
    val createdAt = timestampWithTimeZone("created_at")
    val archivedAt = timestampWithTimeZone("archived_at").nullable()
    val agentGuideDocumentId = javaUUID("agent_guide_document_id").nullable()
    val defaultRepository = varchar("default_repository", 201).nullable()

    override val primaryKey = PrimaryKey(id)
}
