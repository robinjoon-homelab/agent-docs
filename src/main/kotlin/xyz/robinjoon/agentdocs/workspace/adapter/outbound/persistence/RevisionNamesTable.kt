package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object RevisionNamesTable : Table("revision_names") {
    val documentId = javaUUID("document_id")
    val revision = integer("revision")
    val name = varchar("name", 100)
    override val primaryKey = PrimaryKey(documentId, revision)
}
