package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import java.time.Instant

// The latest change to one block, for the "changed by Claude 2 minutes ago" marker.
data class BlockMark(
    val blockId: BlockId,
    val actor: Actor,
    val revision: Int,
    val at: Instant,
)
