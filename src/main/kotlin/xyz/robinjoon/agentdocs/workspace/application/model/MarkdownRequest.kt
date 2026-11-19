package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// Which part of a document to read. section is a heading id and covers the blocks up to the next heading of the same
// or a higher level. The cursor identifies a character boundary in the selected canonical output.
data class MarkdownRequest(
    val format: MarkdownFormat,
    val section: BlockId?,
    val blocks: List<BlockId>?,
    val cursor: Int,
    val revision: Int? = null,
) {
    init {
        if (cursor < 0) {
            throw InvalidRequestException("cursor must not be negative")
        }
        if (section != null && blocks != null) {
            throw InvalidRequestException("pass either section or blocks, not both")
        }
    }
}
