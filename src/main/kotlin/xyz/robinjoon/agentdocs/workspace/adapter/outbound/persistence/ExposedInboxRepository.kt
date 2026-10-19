package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Delivery
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxRecipient
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
import xyz.robinjoon.agentdocs.workspace.domain.inbox.NoticeTarget
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.time.Instant
import java.util.UUID

class ExposedInboxRepository : InboxRepository {
    override fun addAll(items: List<InboxItem>) {
        InboxItemsTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            val target = item.notice.target
            this[InboxItemsTable.id] = item.id.value
            this[InboxItemsTable.recipient] = item.recipient.login
            this[InboxItemsTable.recipientUserId] = item.recipient.userId
            this[InboxItemsTable.kind] = item.notice.kind
            this[InboxItemsTable.spaceId] = target.spaceId.value
            this[InboxItemsTable.documentId] = target.documentId.value
            this[InboxItemsTable.threadId] = target.threadId.value
            this[InboxItemsTable.commentId] = target.commentId.value
            InboxItemsTable.actor.write(this, item.notice.actor)
            this[InboxItemsTable.excerpt] = item.notice.excerpt
            this[InboxItemsTable.deliveredAt] = item.delivery.deliveredAt.utc()
            this[InboxItemsTable.readAt] = item.delivery.readAt?.utc()
        }
    }

    // Only the read state ever changes.
    override fun update(item: InboxItem): InboxItem {
        val updated = InboxItemsTable.update({ InboxItemsTable.id eq item.id.value }) { it[readAt] = item.delivery.readAt?.utc() }
        check(updated == 1) { "Inbox item to update does not exist: ${item.id}" }
        return requireNotNull(findById(item.id))
    }

    override fun findById(id: InboxItemId): InboxItem? =
        InboxItemsTable
            .selectAll()
            .where {
                InboxItemsTable.id eq id.value
            }.singleOrNull()
            ?.toItem()

    override fun claim(
        login: String,
        userId: UUID,
    ): Int =
        InboxItemsTable.update({ (InboxItemsTable.recipient eq login) and InboxItemsTable.recipientUserId.isNull() }) {
            it[recipientUserId] = userId
        }

    override fun list(
        userId: UUID,
        unreadOnly: Boolean,
        limit: Int,
    ): List<InboxItem> {
        val query = InboxItemsTable.selectAll().where { InboxItemsTable.recipientUserId eq userId }
        if (unreadOnly) query.andWhere { InboxItemsTable.readAt.isNull() }
        return query.orderBy(InboxItemsTable.deliveredAt to SortOrder.DESC).limit(limit).map { it.toItem() }
    }

    override fun countUnread(userId: UUID): Int =
        InboxItemsTable
            .selectAll()
            .where { (InboxItemsTable.recipientUserId eq userId) and InboxItemsTable.readAt.isNull() }
            .count()
            .toInt()

    override fun markAllRead(
        userId: UUID,
        at: Instant,
    ): Int =
        InboxItemsTable.update({ (InboxItemsTable.recipientUserId eq userId) and InboxItemsTable.readAt.isNull() }) {
            it[readAt] =
                at.utc()
        }

    private fun ResultRow.toItem(): InboxItem {
        val target =
            NoticeTarget(
                SpaceId(this[InboxItemsTable.spaceId]),
                DocumentId(this[InboxItemsTable.documentId]),
                ThreadId(this[InboxItemsTable.threadId]),
                CommentId(this[InboxItemsTable.commentId]),
            )
        val notice = Notice.restore(this[InboxItemsTable.kind], target, InboxItemsTable.actor.read(this), this[InboxItemsTable.excerpt])
        val delivery = Delivery(this[InboxItemsTable.deliveredAt].toInstant(), this[InboxItemsTable.readAt]?.toInstant())
        val recipient = InboxRecipient(this[InboxItemsTable.recipient], this[InboxItemsTable.recipientUserId])
        return InboxItem.restore(InboxItemId(this[InboxItemsTable.id]), recipient, notice, delivery)
    }
}
