package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import java.time.Instant
import java.util.UUID

// A person's items are found by user id once they are claimed; see InboxRecipient.
interface InboxRepository {
    fun addAll(items: List<InboxItem>)

    fun update(item: InboxItem): InboxItem

    fun findById(id: InboxItemId): InboxItem?

    // Makes every item delivered to the login and not yet claimed the person's. Answers how many it claimed.
    fun claim(
        login: String,
        userId: UUID,
    ): Int

    // Newest first.
    fun list(
        userId: UUID,
        unreadOnly: Boolean,
        limit: Int,
    ): List<InboxItem>

    fun countUnread(userId: UUID): Int

    fun markAllRead(
        userId: UUID,
        at: Instant,
    ): Int
}
