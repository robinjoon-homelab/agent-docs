package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.InboxView
import xyz.robinjoon.agentdocs.workspace.application.port.input.InboxItemNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.InboxUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import java.time.Clock
import java.util.UUID

// Items about documents that were deleted, or spaces the caller can no longer see, are left out instead of failing.
// Every read first claims the items delivered to the reader's login, so they stay the reader's after a login change.
@Transactional
class InboxService(
    private val inbox: InboxRepository,
    private val documents: DocumentRepository,
    private val spaces: SpaceRepository,
    private val clock: Clock,
) : InboxUseCase {
    override fun list(
        access: Access,
        unreadOnly: Boolean,
    ): List<InboxView> {
        if (access.actor.isAgent) return emptyList()
        val items = inbox.list(readerOf(access), unreadOnly, LIMIT).filter { access.sees(it.notice.target.spaceId) }
        val titles = documents.findByIds(items.map { it.notice.target.documentId }.distinct()).associate { it.id to it.header.title }
        val spacesById = spaces.findByIds(items.map { it.notice.target.spaceId }.toSet()).associateBy { it.id }
        return items.mapNotNull { item ->
            val target = item.notice.target
            val title = titles[target.documentId] ?: return@mapNotNull null
            spacesById[target.spaceId]?.let { InboxView(item, it, title) }
        }
    }

    override fun unreadCount(access: Access): Int = if (access.actor.isAgent) 0 else inbox.countUnread(readerOf(access))

    override fun markRead(
        access: Access,
        id: InboxItemId,
    ) {
        val item =
            inbox.findById(id)?.takeIf { !access.actor.isAgent && it.recipient.isFor(readerOf(access)) }
                ?: throw InboxItemNotFoundException(id)
        item.markRead(clock.instant())?.let(inbox::update)
    }

    override fun markAllRead(access: Access): Int = if (access.actor.isAgent) 0 else inbox.markAllRead(readerOf(access), clock.instant())

    private fun readerOf(access: Access): UUID {
        val actor = access.actor
        inbox.claim(actor.login.lowercase(), actor.userId)
        return actor.userId
    }

    private companion object {
        const val LIMIT = 200
    }
}
