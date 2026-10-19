package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId

interface AssetRepository {
    fun add(asset: Asset): Asset

    fun findById(id: AssetId): Asset?
}
