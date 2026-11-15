package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

interface AssetUseCase {
    // Immutable attachments up to 20MB, with image signatures and declared text/PDF formats checked.
    fun upload(
        context: WriteContext,
        space: SpaceSlug,
        file: UploadedFile,
    ): Asset

    // Downloads a public image and keeps its address as the source.
    fun import(
        context: WriteContext,
        space: SpaceSlug,
        url: String,
    ): Asset

    fun content(
        access: Access,
        id: AssetId,
    ): AssetFile
}
