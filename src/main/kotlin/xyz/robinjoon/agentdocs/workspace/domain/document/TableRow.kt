package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.Collections

// A row keeps its id when it moves or when rows are inserted around it, so a comment on a cell follows the cell.
// Each cell holds its inline markdown as written, pipes included; the GFM table form escapes them (TableSyntax).
@ConsistentCopyVisibility
data class TableRow private constructor(
    val id: BlockId,
    val cells: List<InlineText>,
) {
    companion object {
        fun of(
            id: BlockId,
            cells: List<InlineText>,
        ): TableRow = TableRow(id, Collections.unmodifiableList(cells.toList()))
    }
}
