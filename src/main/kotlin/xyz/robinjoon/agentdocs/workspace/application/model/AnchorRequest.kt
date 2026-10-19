package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// Where a new thread goes, as a client describes it. A text range may give only its quote; the service finds the
// offsets, and asks for start when the quote occurs more than once. A cell anchor takes its quote from the cell.
sealed interface AnchorRequest {
    data class Text(
        val blockId: BlockId,
        val quote: String,
        val start: Int?,
    ) : AnchorRequest

    data class Cell(
        val blockId: BlockId,
        val rowId: BlockId,
        val columnId: BlockId,
    ) : AnchorRequest

    data class Whole(
        val blockId: BlockId,
    ) : AnchorRequest

    data object Document : AnchorRequest
}
