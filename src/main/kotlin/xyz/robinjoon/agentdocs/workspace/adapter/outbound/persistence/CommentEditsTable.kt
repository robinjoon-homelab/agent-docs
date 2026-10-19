package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object CommentEditsTable : Table("comment_edits") {
    val commentId = javaUUID("comment_id")
    val position = integer("edit_position")
    val previousBody = text("previous_body")
    val editedAt = timestampWithTimeZone("edited_at")

    override val primaryKey = PrimaryKey(commentId, position)
}
