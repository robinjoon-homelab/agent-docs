package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class ExportSpaceUnavailableException(
    val space: SpaceSlug,
) : RuntimeException("export space unavailable")
