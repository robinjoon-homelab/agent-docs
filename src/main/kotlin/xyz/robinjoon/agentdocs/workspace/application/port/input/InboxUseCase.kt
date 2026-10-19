package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.InboxView
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId

// The caller's own inbox. An agent has none and always sees an empty one.
interface InboxUseCase {
    fun list(
        access: Access,
        unreadOnly: Boolean,
    ): List<InboxView>

    fun unreadCount(access: Access): Int

    fun markRead(
        access: Access,
        id: InboxItemId,
    )

    fun markAllRead(access: Access): Int
}
