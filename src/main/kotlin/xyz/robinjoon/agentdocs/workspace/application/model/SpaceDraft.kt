package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

data class SpaceDraft(
    val slug: SpaceSlug,
    val profile: SpaceProfile,
)
