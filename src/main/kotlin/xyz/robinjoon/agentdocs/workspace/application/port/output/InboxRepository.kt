package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import java.time.Instant
import java.util.UUID

// A person's items are found by user id once they are claimed; see InboxRecipient.
interface InboxRepository {
    fun moveDocuments(
        documents: Set<DocumentId>,
        space: SpaceId,
    )

    fun redactCommentExcerpt(comment: CommentId)

    fun addAll(items: List<InboxItem>)

    fun update(item: InboxItem): InboxItem

    fun findForRead(
        id: InboxItemId,
        userId: UUID,
        scope: SpaceScope,
    ): InboxItem?

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
        scope: SpaceScope,
    ): List<InboxItem>

    fun countUnread(
        userId: UUID,
        scope: SpaceScope,
    ): Int

    fun markAllRead(
        userId: UUID,
        at: Instant,
        scope: SpaceScope,
    ): Int
}
