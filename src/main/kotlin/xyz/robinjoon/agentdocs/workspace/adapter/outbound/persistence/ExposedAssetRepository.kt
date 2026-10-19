package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin

class ExposedAssetRepository : AssetRepository {
    // A concurrent upload of the same bytes to the same space wins the unique key; this one then returns that asset.
    override fun add(asset: Asset): Asset {
        try {
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
        } catch (exception: ExposedSQLException) {
            if (exception.isUniqueViolation()) return requireNotNull(findByContent(asset.spaceId, asset.content.sha256))
            throw exception
        }
        return requireNotNull(findById(asset.id))
    }

    override fun findById(id: AssetId): Asset? =
        AssetsTable
            .selectAll()
            .where { AssetsTable.id eq id.value }
            .singleOrNull()
            ?.toAsset()

    override fun findByContent(
        space: SpaceId,
        sha256: String,
    ): Asset? =
        AssetsTable
            .selectAll()
            .where { (AssetsTable.spaceId eq space.value) and (AssetsTable.sha256 eq sha256) }
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
