package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId

data class DocumentEventSnapshot(
    val spaceId: SpaceId,
    val version: Int,
    val revision: Int,
    val trashed: Boolean,
    val threads: String,
)
