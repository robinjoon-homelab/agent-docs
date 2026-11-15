package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

data class BlockRestore(
    val revision: Int,
    val blockId: BlockId,
    val baseVersion: Int,
)
