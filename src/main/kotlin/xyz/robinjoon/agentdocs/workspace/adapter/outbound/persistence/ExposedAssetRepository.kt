package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin

class ExposedAssetRepository : AssetRepository {
    override fun add(asset: Asset): Asset {
        AssetsTable.insert {
            it[id] = asset.id.value
            it[spaceId] = asset.spaceId.value
            it[mediaType] = asset.content.mediaType
            it[sizeBytes] = asset.content.size
            it[sha256] = asset.content.sha256
            it[fileName] = asset.origin.fileName
            it[sourceUrl] = asset.origin.sourceUrl
            uploader.write(it, asset.origin.uploadedBy)
            it[uploadedAt] = asset.origin.uploadedAt.utc()
        }
        return requireNotNull(findById(asset.id))
    }

    override fun findById(id: AssetId): Asset? =
        AssetsTable
            .selectAll()
            .where { AssetsTable.id eq id.value }
            .singleOrNull()
            ?.toAsset()

    private fun ResultRow.toAsset(): Asset {
        val content = AssetContent(this[AssetsTable.mediaType], this[AssetsTable.sizeBytes], this[AssetsTable.sha256])
        val origin =
            AssetOrigin(
                this[AssetsTable.fileName],
                this[AssetsTable.sourceUrl],
                AssetsTable.uploader.read(this),
                this[AssetsTable.uploadedAt].toInstant(),
            )
        return Asset.restore(AssetId(this[AssetsTable.id]), SpaceId(this[AssetsTable.spaceId]), content, origin)
    }
}
