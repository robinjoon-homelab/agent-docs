package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

sealed interface BlockPosition {
    data object Start : BlockPosition

    data object End : BlockPosition

    data class Before(
        val id: BlockId,
    ) : BlockPosition

    data class After(
        val id: BlockId,
    ) : BlockPosition
}
