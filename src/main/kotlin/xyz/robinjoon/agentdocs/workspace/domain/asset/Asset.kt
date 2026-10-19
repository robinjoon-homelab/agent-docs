package xyz.robinjoon.agentdocs.workspace.domain.asset

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId

// A file uploaded into a space. Image blocks refer to it by id, so an asset is never deleted while any revision may
// still show it.
@AggregateRoot
@ConsistentCopyVisibility
data class Asset private constructor(
    val id: AssetId,
    val spaceId: SpaceId,
    val content: AssetContent,
    val origin: AssetOrigin,
) {
    companion object {
        fun upload(
            id: AssetId,
            spaceId: SpaceId,
            content: AssetContent,
            origin: AssetOrigin,
        ): Asset = Asset(id, spaceId, content, origin)

        fun restore(
            id: AssetId,
            spaceId: SpaceId,
            content: AssetContent,
            origin: AssetOrigin,
        ): Asset = Asset(id, spaceId, content, origin)
    }
}
