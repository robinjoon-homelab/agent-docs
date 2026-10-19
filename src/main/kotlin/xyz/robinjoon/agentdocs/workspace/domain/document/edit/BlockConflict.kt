package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// A block someone else changed after the writer read it. current is the block's markdown now, or null when it was
// deleted, so the writer can read it again and retry without another request.
data class BlockConflict(
    val blockId: BlockId,
    val currentHash: String?,
    val current: String?,
)
