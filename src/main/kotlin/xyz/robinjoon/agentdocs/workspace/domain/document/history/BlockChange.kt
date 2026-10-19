package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock

data class BlockChange(
    val blockId: BlockId,
    val kind: BlockChangeKind,
    val before: Block?,
    val after: Block?,
) {
    // For a table, the cells whose text differs, including every cell of an added row or column, so a viewer can
    // highlight cells instead of the whole table.
    fun changedCells(): List<CellRef> {
        val old = before as? TableBlock
        val new = after as? TableBlock ?: return emptyList()
        return new.rows.flatMap { row ->
            new.columns.mapIndexedNotNull { position, column ->
                CellRef(row.id, column.id).takeIf { old?.let { cellText(it, row.id, column.id) } != row.cells[position] }
            }
        }
    }

    private fun cellText(
        table: TableBlock,
        rowId: BlockId,
        columnId: BlockId,
    ) = table.row(rowId)?.cells?.getOrNull(table.columnIndex(columnId))
}
