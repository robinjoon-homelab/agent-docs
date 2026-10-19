package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// The settings to change; null keeps the current value.
data class SpaceChange(
    val slug: SpaceSlug?,
    val name: String?,
    val description: String?,
    val links: List<ExternalLink>?,
)
