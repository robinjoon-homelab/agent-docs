package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentAssetBinding
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentAssetStore
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import java.util.UUID

class DocumentTransferAssets(
    private val assets: AssetRepository,
    private val references: DocumentAssetStore,
) {
    fun move(
        documents: List<Document>,
        target: SpaceId,
    ) {
        val ids = documents.map { it.id }.toSet()
        val resolved = resolvedReferences(documents)
        val current = storedAssets(resolved)
        requireSpaces(documents, resolved, current)
        val shared = references.sharedOutside(current.keys, ids)
        val moved = current.mapValues { (id, asset) -> relocate(asset, target, id in shared) }
        references.bind(resolved.map { it.copy(storedAssetId = moved.getValue(it.storedAssetId).id) })
    }

    private fun resolvedReferences(documents: List<Document>): List<DocumentAssetBinding> {
        val bindings = references.bindings(documents.map { it.id }.toSet()).associateBy { it.documentId to it.referencedAssetId }
        return documents.flatMap { document ->
            references.references(document.id).map {
                bindings[document.id to it] ?: DocumentAssetBinding(document.id, it, it)
            }
        }
    }

    private fun storedAssets(bindings: List<DocumentAssetBinding>): Map<AssetId, Asset> =
        bindings.map { it.storedAssetId }.distinct().sortedBy { it.value }.associateWith { id ->
            assets.findByIdForUpdate(id) ?: throw AssetNotFoundException(id)
        }

    private fun requireSpaces(
        documents: List<Document>,
        bindings: List<DocumentAssetBinding>,
        current: Map<AssetId, Asset>,
    ) {
        val spaces = documents.associate { it.id to it.spaceId }
        bindings.forEach {
            if (current.getValue(it.storedAssetId).spaceId != spaces[it.documentId]) throw AssetNotFoundException(it.referencedAssetId)
        }
    }

    private fun relocate(
        asset: Asset,
        target: SpaceId,
        shared: Boolean,
    ): Asset =
        when {
            asset.spaceId == target -> asset
            shared -> assets.add(asset.copyTo(AssetId(UUID.randomUUID()), target))
            else -> assets.update(asset.moveTo(target))
        }
}
