package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import java.time.Instant

// A document is stored together with its revision history: every write passes the revision it produces, so the
// current state and the history can never disagree.
interface DocumentRepository {
    // Fails with PathTakenException when another document outside the trash has the same path in the space.
    fun add(
        document: Document,
        revision: Revision,
    ): Document

    // Writes a document loaded for update in the same transaction. A new revision is appended; an amended revision
    // replaces the latest one, which must have the same number.
    fun update(
        document: Document,
        revision: Revision,
        amended: Boolean,
    ): Document

    fun findById(id: DocumentId): Document?

    // Keeps the order of the ids and skips ids that do not exist.
    fun findByIds(ids: List<DocumentId>): List<Document>

    // Locks the document until the transaction ends, so writes to one document apply one after another.
    fun findByIdForUpdate(id: DocumentId): Document?

    fun findByPath(
        space: SpaceId,
        path: DocumentPath,
    ): Document?

    // Newest change first.
    fun list(
        spaces: Set<SpaceId>,
        filter: DocumentFilter,
        limit: Int,
    ): List<DocumentListing>

    fun trashedBefore(cutoff: Instant): List<DocumentId>

    // Removes the document, its revisions, and everything else that only exists for it. Assets stay.
    fun delete(id: DocumentId)
}
