package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentAssetBinding
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// Logical references belong to a document; bindings resolve them without rewriting immutable markdown.
interface DocumentAssetStore {
    fun references(document: DocumentId): Set<AssetId>

    fun bindings(documents: Set<DocumentId>): List<DocumentAssetBinding>

    fun sharedOutside(
        assets: Set<AssetId>,
        documents: Set<DocumentId>,
    ): Set<AssetId>

    fun bind(bindings: List<DocumentAssetBinding>)
}
