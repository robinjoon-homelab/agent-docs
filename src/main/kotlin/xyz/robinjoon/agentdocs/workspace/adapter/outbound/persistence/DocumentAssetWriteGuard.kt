package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.AssetReferences
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownAssetReferences
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread

// Reference writers and transfers take the same asset locks, so an exclusive move cannot race a new retained reference.
internal object DocumentAssetWriteGuard {
    fun document(document: Document) =
        requireSpace(document.id, document.spaceId, AssetReferences.of(document.blocks), AssetReferences.expectedTypes(document.blocks))

    fun thread(thread: CommentThread) {
        val comments =
            thread.comments.filterNot { it.isDeleted }.flatMap {
                listOf(it.body) + it.history.edits.map { edit -> edit.previousBody }
            }
        val proposals = thread.collaboration.suggestions.flatMap { listOf(it.basis.before, it.basis.after) }
        requireSpace(
            thread.location.documentId,
            thread.location.spaceId,
            (comments + proposals).flatMap { MarkdownAssetReferences.of(it) }.toSet(),
            emptyMap(),
        )
    }

    // A drawing's assets must really exist with the type their role needs; unlike legacy image placeholders, a drawing
    // cannot point at something that was never uploaded.
    private fun requireSpace(
        document: DocumentId,
        space: SpaceId,
        references: Set<AssetId>,
        expected: Map<AssetId, AssetMediaType>,
    ) {
        if (references.isEmpty()) return
        val bindings =
            DocumentAssetBindingsTable
                .selectAll()
                .where { DocumentAssetBindingsTable.documentId eq document.value }
                .associate {
                    AssetId(it[DocumentAssetBindingsTable.referencedAssetId]) to AssetId(it[DocumentAssetBindingsTable.storedAssetId])
                }
        val stored = references.groupBy { bindings[it] ?: it }
        stored.keys.sortedBy { it.value }.forEach { id ->
            // Keep legacy handling of unknown placeholders; a known asset may only be referenced through its current boundary.
            val row =
                AssetsTable
                    .selectAll()
                    .where { AssetsTable.id eq id.value }
                    .forUpdate()
                    .singleOrNull()
            if (row != null && row[AssetsTable.spaceId] != space.value) {
                throw InvalidDocumentException("asset $id is outside this document's space")
            }
            stored.getValue(id).mapNotNull(expected::get).forEach { type -> requireType(id, row?.get(AssetsTable.mediaType), type) }
        }
    }

    private fun requireType(
        id: AssetId,
        actual: AssetMediaType?,
        needed: AssetMediaType,
    ) {
        if (actual != needed) {
            throw InvalidDocumentException("a drawing needs an uploaded ${needed.mime} asset in this space, but $id is not one")
        }
    }
}
