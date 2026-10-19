package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.ThreadCriteria
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

// add and update store the whole thread with its comments and return it read back.
interface ThreadRepository {
    fun add(thread: CommentThread): CommentThread

    fun update(thread: CommentThread): CommentThread

    fun findById(id: ThreadId): CommentThread?

    fun findByIdForUpdate(id: ThreadId): CommentThread?

    fun findByCommentForUpdate(id: CommentId): CommentThread?

    // Oldest first, so a rail lists threads in the order they were opened.
    fun findByDocument(document: DocumentId): List<CommentThread>

    // Most recently changed first.
    fun find(criteria: ThreadCriteria): List<CommentThread>

    // For each revision of the document, the threads it was linked to.
    fun linksByRevision(document: DocumentId): Map<Int, List<ThreadId>>
}
