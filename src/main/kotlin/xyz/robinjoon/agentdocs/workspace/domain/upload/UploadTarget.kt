package xyz.robinjoon.agentdocs.workspace.domain.upload

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import java.util.UUID

// What a grant lets through: a file into this space, by this agent, shown with this alt text.
data class UploadTarget(
    val spaceId: SpaceId,
    val issuer: Actor,
    val alt: String,
) {
    // A person has the web editor, and the connection is what an upload token names.
    val connectionId: UUID =
        issuer.agent?.connectionId ?: throw InvalidAssetException("only an agent is given an upload grant")
}
