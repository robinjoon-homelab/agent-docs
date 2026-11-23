package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// Which part of a document to read. section is a heading id and covers the blocks up to the next heading of the same
// or a higher level. The cursor identifies a character boundary in the selected canonical output. flowQuery lists
// matching screens and elements of a deployed flow document instead of its body.
data class MarkdownRequest(
    val format: MarkdownFormat,
    val section: BlockId?,
    val blocks: List<BlockId>?,
    val cursor: Int,
    val revision: Int? = null,
    val flowQuery: FlowQuery? = null,
) {
    init {
        if (cursor < 0) {
            throw InvalidRequestException("cursor must not be negative")
        }
        if (section != null && blocks != null) {
            throw InvalidRequestException("pass either section or blocks, not both")
        }
        if (flowQuery != null && (section != null || blocks != null)) {
            throw InvalidRequestException(
                "pass either a flow query or section/blocks, not both",
                "find candidates with the flow query, then read them with blocks or section",
            )
        }
    }
}
