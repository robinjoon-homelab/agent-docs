package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus

// One change to a thread: any of a new anchor, a new status, and a revision that answered it. They apply together or
// not at all.
data class ThreadChange(
    val anchor: AnchorRequest? = null,
    val status: ThreadStatus? = null,
    val linkRevision: Int? = null,
) {
    val isEmpty: Boolean get() = anchor == null && status == null && linkRevision == null
}
