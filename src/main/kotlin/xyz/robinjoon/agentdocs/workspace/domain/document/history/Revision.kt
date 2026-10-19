package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// The full content of a document at one revision. Revisions are appended and never rewritten, except that a person's
// continued typing within the grouping window replaces the content of their own latest revision.
data class Revision(
    val documentId: DocumentId,
    val number: Int,
    val content: RevisionContent,
    val change: RevisionChange,
) {
    companion object {
        fun of(
            document: Document,
            change: RevisionChange,
        ): Revision =
            Revision(document.id, document.revision, RevisionContent.of(document.header, document.blocks, document.isTrashed), change)
    }
}
