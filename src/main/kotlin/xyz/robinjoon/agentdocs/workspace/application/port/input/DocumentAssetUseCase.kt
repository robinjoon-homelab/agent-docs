package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

interface DocumentAssetUseCase {
    fun content(
        access: Access,
        document: DocumentId,
        reference: AssetId,
    ): AssetFile
}
