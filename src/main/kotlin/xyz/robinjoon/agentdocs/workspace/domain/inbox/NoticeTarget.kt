package xyz.robinjoon.agentdocs.workspace.domain.inbox

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

data class NoticeTarget(
    val spaceId: SpaceId,
    val documentId: DocumentId,
    val threadId: ThreadId,
    val commentId: CommentId,
)
