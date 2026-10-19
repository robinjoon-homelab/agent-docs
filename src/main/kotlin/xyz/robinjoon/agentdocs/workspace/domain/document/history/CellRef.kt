package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

data class CellRef(
    val rowId: BlockId,
    val columnId: BlockId,
)
