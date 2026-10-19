package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.BlockMark
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionSummary
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision

interface RevisionCatalog {
    fun find(
        document: DocumentId,
        number: Int,
    ): Revision?

    // Newest first.
    fun list(
        document: DocumentId,
        filter: RevisionFilter,
    ): List<RevisionSummary>

    // The latest change to each block that is still in the document.
    fun marks(document: DocumentId): List<BlockMark>
}
