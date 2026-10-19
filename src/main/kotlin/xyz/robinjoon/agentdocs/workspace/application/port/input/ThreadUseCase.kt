package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadChange
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

// Writes need comment permission. Mentions, replies to a person's thread, and comments on a document assigned to
// someone go to those people's inboxes. Nothing here ever starts an agent.
interface ThreadUseCase {
    fun open(
        context: WriteContext,
        document: DocumentId,
        anchor: AnchorRequest,
        body: String,
    ): ThreadView

    // markAnswered also moves an open thread to answered, for an agent that handled the request.
    fun reply(
        context: WriteContext,
        thread: ThreadId,
        body: String,
        markAnswered: Boolean,
    ): ThreadView

    fun editComment(
        context: WriteContext,
        comment: CommentId,
        body: String,
    ): ThreadView

    fun deleteComment(
        context: WriteContext,
        comment: CommentId,
    ): ThreadView

    // An agent may not resolve a thread a person opened; that fails with ThreadActionException and changes nothing.
    fun change(
        context: WriteContext,
        thread: ThreadId,
        change: ThreadChange,
    ): ThreadView

    fun forDocument(
        access: Access,
        document: DocumentId,
    ): List<ThreadView>

    // Each result carries the anchored block and its neighbors, at most ThreadQuery.MAX_RESULTS results.
    fun query(
        access: Access,
        query: ThreadQuery,
    ): List<ThreadView>
}
