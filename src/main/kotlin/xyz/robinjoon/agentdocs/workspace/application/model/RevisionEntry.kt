package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

// A revision with the threads it was written in answer to.
data class RevisionEntry(
    val revision: RevisionSummary,
    val threads: List<ThreadId>,
)
