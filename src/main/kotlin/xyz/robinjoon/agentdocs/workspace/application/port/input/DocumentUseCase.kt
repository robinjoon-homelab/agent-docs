package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.BlockRestore
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionLabel
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

// Writes lock the document and require edit permission. Content versions and immutable history advance separately.
interface DocumentUseCase {
    fun upgradeFlow(
        context: WriteContext,
        id: DocumentId,
        baseVersion: Int,
    ): DocumentWrite

    fun create(
        context: WriteContext,
        space: SpaceSlug,
        draft: DocumentDraft,
    ): DocumentWrite

    // Replaces the whole body only when baseVersion matches; stale writes return the current content.
    fun save(
        context: WriteContext,
        id: DocumentId,
        baseVersion: Int,
        blocks: List<Block>,
    ): DocumentWrite

    // Applies the operations all or nothing. A guarded block that changed since it was read fails the whole edit with
    // EditConflictException. answering links the new revision to that thread of the same document.
    fun edit(
        context: WriteContext,
        id: DocumentId,
        operations: List<EditOperation>,
        answering: ThreadId?,
    ): DocumentWrite

    // Requires the current content version. A new path
    // under the document's own path fails, and so does one where the document or any document under it would land
    // on a path in use, with PathTakenException naming it.
    fun changeHeader(
        context: WriteContext,
        id: DocumentId,
        change: HeaderChange,
    ): DocumentWrite

    fun trash(
        context: WriteContext,
        id: DocumentId,
    ): DocumentWrite

    fun untrash(
        context: WriteContext,
        id: DocumentId,
    ): DocumentWrite

    // Owner only, and only for a document already in the trash.
    fun purge(
        context: WriteContext,
        id: DocumentId,
    )

    // Writes the blocks and title of an earlier revision as a new revision. History is never rewound.
    // baseVersion is required and must match the current content version.
    fun restore(
        context: WriteContext,
        id: DocumentId,
        revision: Int,
        baseVersion: Int?,
    ): DocumentWrite

    // Removes documents that have been in the trash for 30 days. Returns how many were removed.
    fun nameRevision(
        context: WriteContext,
        id: DocumentId,
        revision: Int,
        name: String,
    ): RevisionLabel

    fun restoreBlock(
        context: WriteContext,
        id: DocumentId,
        request: BlockRestore,
    ): DocumentWrite

    fun purgeExpiredTrash(): Int

    fun finalizeEdits(
        context: WriteContext,
        id: DocumentId,
        baseVersion: Int,
    ): DocumentWrite

    fun finalizeExpiredEdits(): Int

    // Gives every folder path that has no document an empty one, written in the name of the person with the owner's
    // login. Paths that already have a document are left alone, so running it again adds nothing. Returns the
    // documents it created.
    fun fillMissingParents(owner: Access): List<Document>
}
