package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionEntry
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionDiff
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// Reads need read permission on the document's space; a document in a space the caller cannot see is not found.
interface DocumentQueryUseCase {
    // A person opening a document is remembered for the revisit metric.
    fun open(
        access: Access,
        id: DocumentId,
    ): DocumentView

    fun list(
        access: Access,
        space: SpaceSlug,
        filter: DocumentFilter,
    ): List<DocumentListing>

    // Documents of every visible space, most recently changed first.
    fun recent(
        access: Access,
        limit: Int,
    ): List<DocumentListing>

    fun revisions(
        access: Access,
        id: DocumentId,
        filter: RevisionFilter,
    ): List<RevisionEntry>

    fun revision(
        access: Access,
        id: DocumentId,
        number: Int,
    ): Revision

    // to null compares with the current revision.
    fun diff(
        access: Access,
        id: DocumentId,
        from: Int,
        to: Int?,
    ): RevisionDiff

    // At most about 20,000 tokens of markdown; nextCursor continues a longer document.
    fun markdown(
        access: Access,
        id: DocumentId,
        request: MarkdownRequest,
    ): MarkdownChunk
}
