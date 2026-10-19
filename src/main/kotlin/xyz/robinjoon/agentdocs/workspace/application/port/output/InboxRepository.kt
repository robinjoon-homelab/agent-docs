package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import java.time.Instant

interface InboxRepository {
    fun addAll(items: List<InboxItem>)

    fun update(item: InboxItem): InboxItem

    fun findById(id: InboxItemId): InboxItem?

    // Newest first.
    fun list(
        recipient: String,
        unreadOnly: Boolean,
        limit: Int,
    ): List<InboxItem>

    fun countUnread(recipient: String): Int

    fun markAllRead(
        recipient: String,
        at: Instant,
    ): Int
}
