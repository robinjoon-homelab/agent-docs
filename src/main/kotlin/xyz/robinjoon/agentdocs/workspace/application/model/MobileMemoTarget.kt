package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

data class MobileMemoTarget(
    val id: DocumentId,
    val version: Int,
    val blocks: List<Block>,
)
