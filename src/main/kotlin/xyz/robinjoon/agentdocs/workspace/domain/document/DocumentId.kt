package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.UUID

// Stays the same through renames, moves between folders, and restores, so history, links, and comments keep pointing
// at the same document.
@JvmInline
value class DocumentId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
