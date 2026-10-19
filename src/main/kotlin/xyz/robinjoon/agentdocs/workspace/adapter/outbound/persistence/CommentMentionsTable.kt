package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object CommentMentionsTable : Table("comment_mentions") {
    val commentId = javaUUID("comment_id")
    val login = varchar("login", 39)

    override val primaryKey = PrimaryKey(commentId, login)
}
