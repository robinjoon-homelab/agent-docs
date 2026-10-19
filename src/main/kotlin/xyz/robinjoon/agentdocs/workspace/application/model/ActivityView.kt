package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.workspace.domain.space.Space

// A log entry with the space and document title it concerns, when they still exist.
data class ActivityView(
    val entry: ActivityEntry,
    val space: Space?,
    val documentTitle: String?,
)
