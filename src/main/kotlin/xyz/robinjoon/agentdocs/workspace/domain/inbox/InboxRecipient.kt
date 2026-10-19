package xyz.robinjoon.agentdocs.workspace.domain.inbox

import java.util.UUID

// Who an item is for. An item is delivered to a login, the name comments use. The next time that person opens their
// inbox it becomes theirs by user id, so a later change of their GitHub login keeps it.
data class InboxRecipient(
    val login: String,
    val userId: UUID?,
) {
    fun isFor(userId: UUID): Boolean = this.userId == userId
}
