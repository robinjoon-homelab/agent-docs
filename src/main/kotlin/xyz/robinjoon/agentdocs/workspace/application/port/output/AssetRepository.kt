package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId

interface AssetRepository {
    fun add(asset: Asset): Asset

    fun findById(id: AssetId): Asset?

    // The asset of this space with the same bytes, so an upload of an identical file returns the existing asset.
    fun findByContent(
        space: SpaceId,
        sha256: String,
    ): Asset?
}
