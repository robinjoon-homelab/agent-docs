package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Document

// A document a write changed besides the one it names, as it is after the write: a parent it created or brought
// back from the trash, or a descendant it moved, trashed, or restored along.
data class RelatedWrite(
    val document: Document,
    val change: RelatedChange,
)

enum class RelatedChange {
    CREATED,
    MOVED,
    TRASHED,
    RESTORED,
}
