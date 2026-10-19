package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

internal object ThreadCommentsTable : Table("thread_comments") {
    val id = javaUUID("id")
    val threadId = javaUUID("thread_id")
    val position = integer("comment_position")
    val author = ActorColumns(this, "author")
    val body = text("body")
    val createdAt = timestampWithTimeZone("created_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
