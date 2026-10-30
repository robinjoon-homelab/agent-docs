package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.BlockMark
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionSummary
import xyz.robinjoon.agentdocs.workspace.domain.Actor
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

    // The person with this login as the latest revision by them or their agents names them, or null if they never
    // wrote. Used to write in a person's name when no request carries them.
    fun authorOf(login: String): Actor?
}
