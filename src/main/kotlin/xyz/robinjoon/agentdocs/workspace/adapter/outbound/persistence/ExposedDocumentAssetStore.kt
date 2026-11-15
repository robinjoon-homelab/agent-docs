package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentAssetBinding
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

class ExposedDocumentAssetStore : DocumentAssetStore {
    override fun references(document: DocumentId): Set<AssetId> = DocumentAssetReferences.read(setOf(document))[document].orEmpty()

    override fun bindings(documents: Set<DocumentId>): List<DocumentAssetBinding> {
        if (documents.isEmpty()) return emptyList()
        return DocumentAssetBindingsTable
            .selectAll()
            .where { DocumentAssetBindingsTable.documentId inList documents.map { it.value } }
            .map(::binding)
    }

    override fun sharedOutside(
        assets: Set<AssetId>,
        documents: Set<DocumentId>,
    ): Set<AssetId> {
        if (assets.isEmpty()) return emptySet()
        val bindingRows = DocumentAssetBindingsTable.selectAll().map(::binding)
        val bound = bindingRows.associateBy { it.documentId to it.referencedAssetId }
        val used =
            DocumentAssetReferences.read(null).filterKeys { it !in documents }.flatMap { (doc, refs) ->
                refs.map { bound[doc to it]?.storedAssetId ?: it }
            }
        return (used + bindingRows.filter { it.documentId !in documents }.map { it.storedAssetId }).filter { it in assets }.toSet()
    }

    private fun binding(row: ResultRow): DocumentAssetBinding =
        DocumentAssetBinding(
            DocumentId(row[DocumentAssetBindingsTable.documentId]),
            AssetId(row[DocumentAssetBindingsTable.referencedAssetId]),
            AssetId(row[DocumentAssetBindingsTable.storedAssetId]),
        )

    override fun bind(bindings: List<DocumentAssetBinding>) {
        bindings.forEach { binding ->
            DocumentAssetBindingsTable.deleteWhere {
                (documentId eq binding.documentId.value) and (referencedAssetId eq binding.referencedAssetId.value)
            }
            DocumentAssetBindingsTable.insert {
                it[documentId] = binding.documentId.value
                it[referencedAssetId] = binding.referencedAssetId.value
                it[storedAssetId] = binding.storedAssetId.value
            }
        }
    }
}
