package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

// Every write needs edit permission on the document's space, takes a row lock on the document, and adds a revision,
// except that a person's saves within 30 seconds of their own last web save replace that revision.
interface DocumentUseCase {
    fun create(
        context: WriteContext,
        space: SpaceSlug,
        draft: DocumentDraft,
    ): DocumentWrite

    // The whole block list as the writer has it, based on the revision it last read. Blocks changed by others since
    // are merged in; a block both changed fails with EditConflictException listing every such block.
    fun save(
        context: WriteContext,
        id: DocumentId,
        baseRevision: Int,
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

    // Fails with StaleRevisionException when change.baseRevision is set and is not the current revision.
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
    // baseRevision, when given, must be the current revision: restoring drops every later change from the document.
    fun restore(
        context: WriteContext,
        id: DocumentId,
        revision: Int,
        baseRevision: Int?,
    ): DocumentWrite

    // Removes documents that have been in the trash for 30 days. Returns how many were removed.
    fun purgeExpiredTrash(): Int
}
