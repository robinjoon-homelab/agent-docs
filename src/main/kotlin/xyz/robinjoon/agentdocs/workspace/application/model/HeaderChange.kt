package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus

// The header fields to change; null keeps the current value. assignee is wrapped so that "clear the assignee" and
// "keep the assignee" stay different. baseRevision, when given, is the revision the writer last saw; the change is
// refused if the document has moved on since.
data class HeaderChange(
    val title: String?,
    val path: DocumentPath?,
    val status: DocumentStatus?,
    val tags: List<String>?,
    val assignee: AssigneeChange?,
    val baseRevision: Int?,
)
