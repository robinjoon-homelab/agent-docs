package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread

// A thread with where its anchor sits now. context is filled for queries, so an agent sees what the thread is about
// without reading the whole document. written is the comment a write added, so a retry answers with the same one.
data class ThreadView(
    val thread: CommentThread,
    val resolution: AnchorResolution,
    val context: ThreadContext?,
    val written: CommentId? = null,
)
