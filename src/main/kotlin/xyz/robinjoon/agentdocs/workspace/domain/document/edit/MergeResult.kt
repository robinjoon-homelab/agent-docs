package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import java.util.Collections

class MergeResult(
    blocks: List<Block>,
    conflicts: List<BlockConflict>,
) {
    val blocks: List<Block> = Collections.unmodifiableList(blocks.toList())
    val conflicts: List<BlockConflict> = Collections.unmodifiableList(conflicts.toList())
}
