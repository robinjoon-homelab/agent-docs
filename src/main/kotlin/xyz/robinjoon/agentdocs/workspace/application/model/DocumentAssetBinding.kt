package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

data class DocumentAssetBinding(
    val documentId: DocumentId,
    val referencedAssetId: AssetId,
    val storedAssetId: AssetId,
)
