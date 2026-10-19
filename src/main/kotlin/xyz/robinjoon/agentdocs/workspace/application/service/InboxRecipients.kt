package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.Mentions

// Who hears about a new comment, and why. Each person gets at most one item, picked in the order mention, reply,
// assignment. The writer is skipped, but a person still hears about what their own agent wrote.
internal object InboxRecipients {
    fun of(
        thread: CommentThread,
        comment: Comment,
        assignee: String?,
    ): Map<InboxKind, Set<String>> {
        val writer = comment.author
        val skipped = if (writer.isAgent) emptySet() else setOf(writer.login.lowercase())
        val mentioned = Mentions.of(comment.body) - skipped
        val opener =
            thread.author
                .takeIf { !it.isAgent && thread.comments.size > 1 }
                ?.login
                ?.lowercase()
        val replied = setOfNotNull(opener) - skipped - mentioned
        val assigned = setOfNotNull(assignee?.lowercase()) - skipped - mentioned - replied
        return mapOf(InboxKind.MENTION to mentioned, InboxKind.REPLY to replied, InboxKind.ASSIGNED_COMMENT to assigned).filterValues {
            it.isNotEmpty()
        }
    }
}
