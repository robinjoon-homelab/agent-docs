package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

data class SpaceReference(
    val id: SpaceId,
    val slug: SpaceSlug,
    val name: String,
)
