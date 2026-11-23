package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// One step of an atomic edit. expectedHash is the block hash the writer saw; when the block changed since, the edit
// stops instead of overwriting someone else's change.
sealed interface EditOperation {
    data class DesignField(
        val patch: DesignFieldPatch,
    ) : EditOperation

    // The first new block keeps the target's id, so comments on the block stay attached.
    data class Replace(
        val target: BlockId,
        val expectedHash: String,
        val blocks: List<Block>,
        val flowDefaults: xyz.robinjoon.agentdocs.workspace.domain.document.FlowDefaults =
            xyz.robinjoon.agentdocs.workspace.domain.document
                .FlowDefaults(),
    ) : EditOperation

    data class Insert(
        val position: BlockPosition,
        val blocks: List<Block>,
        val flowDefaults: Map<BlockId, xyz.robinjoon.agentdocs.workspace.domain.document.FlowDefaults> = emptyMap(),
    ) : EditOperation

    data class Delete(
        val target: BlockId,
        val expectedHash: String,
    ) : EditOperation

    data class Move(
        val target: BlockId,
        val expectedHash: String,
        val position: BlockPosition,
    ) : EditOperation

    // Replaces exactly one occurrence of find in the block's markdown.
    data class ReplaceText(
        val target: BlockId,
        val expectedHash: String,
        val find: String,
        val replacement: String,
    ) : EditOperation
}
