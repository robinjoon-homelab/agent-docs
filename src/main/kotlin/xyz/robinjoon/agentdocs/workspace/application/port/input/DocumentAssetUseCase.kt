package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.model.AssetSource
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

interface DocumentAssetUseCase {
    fun content(
        access: Access,
        document: DocumentId,
        reference: AssetId,
    ): AssetFile

    // The stored text of an HTML, SVG, or drawing scene attachment this document refers to; a scene comes with its texts.
    fun source(
        access: Access,
        document: DocumentId,
        reference: AssetId,
    ): AssetSource
}
