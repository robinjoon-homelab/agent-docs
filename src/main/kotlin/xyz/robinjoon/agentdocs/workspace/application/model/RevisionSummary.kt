package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange

// A revision in the history list, without its content.
data class RevisionSummary(
    val number: Int,
    val title: String,
    val change: RevisionChange,
)
