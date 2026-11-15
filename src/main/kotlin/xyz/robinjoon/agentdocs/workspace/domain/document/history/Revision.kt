package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// A confirmed snapshot is immutable; pending snapshots are excluded from history queries.
data class Revision(
    val documentId: DocumentId,
    val number: Int,
    val content: RevisionContent,
    val change: RevisionChange,
    val version: Int = number,
) {
    companion object {
        fun of(
            document: Document,
            change: RevisionChange,
        ): Revision =
            Revision(
                document.id,
                document.revision,
                RevisionContent.of(document.header, document.blocks, document.isTrashed),
                change,
                document.version,
            )
    }
}
