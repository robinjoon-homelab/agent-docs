package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object DocumentBlockMarksTable : Table("document_block_marks") {
    val documentId = javaUUID("document_id")
    val blockId = varchar("block_id", 16)
    val revision = integer("revision")
    val actor = ActorColumns(this, "actor")
    val changedAt = timestampWithTimeZone("changed_at")

    override val primaryKey = PrimaryKey(documentId, blockId)
}
