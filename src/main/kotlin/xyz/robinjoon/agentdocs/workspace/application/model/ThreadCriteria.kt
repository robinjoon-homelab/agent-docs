package xyz.robinjoon.agentdocs.workspace.application.model

// A thread query narrowed to the spaces the caller may read, as the repository receives it. An empty set of spaces
// matches nothing.
data class ThreadCriteria(
    val query: ThreadQuery,
    val limit: Int,
)
