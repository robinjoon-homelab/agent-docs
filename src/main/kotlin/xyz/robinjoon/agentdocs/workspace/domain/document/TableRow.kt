package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.Collections

// A row keeps its id when it moves or when rows are inserted around it, so a comment on a cell follows the cell.
// A pipe inside a cell is always stored escaped, which keeps the GFM table form unambiguous.
@ConsistentCopyVisibility
data class TableRow private constructor(
    val id: BlockId,
    val cells: List<InlineText>,
) {
    companion object {
        private val unescapedPipe = Regex("(?<!\\\\)\\|")

        fun of(
            id: BlockId,
            cells: List<InlineText>,
        ): TableRow = TableRow(id, Collections.unmodifiableList(cells.map { InlineText(it.markdown.replace(unescapedPipe, "\\\\|")) }))
    }
}
