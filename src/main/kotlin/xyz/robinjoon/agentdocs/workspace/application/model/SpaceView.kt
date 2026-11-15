package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.space.Space

// A space with its agent guide as readers get it. guide is null when the space names none.
data class SpaceView(
    val space: Space,
    val guide: SpaceGuide?,
)
