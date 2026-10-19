package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind

internal object InboxItemsTable : Table("inbox_items") {
    val id = javaUUID("id")
    val recipient = varchar("recipient", 39)
    val kind = enumerationByName("kind", 20, InboxKind::class)
    val spaceId = javaUUID("space_id")
    val documentId = javaUUID("document_id")
    val threadId = javaUUID("thread_id")
    val commentId = javaUUID("comment_id")
    val actor = ActorColumns(this, "actor")
    val excerpt = varchar("excerpt", 200)
    val deliveredAt = timestampWithTimeZone("delivered_at")
    val readAt = timestampWithTimeZone("read_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
