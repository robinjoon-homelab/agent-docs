package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ColumnAlignment
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableColumn
import xyz.robinjoon.agentdocs.workspace.domain.document.TableRow

// Reads a GFM table that starts at a header row followed by a delimiter row. Column ids come from the annotation in
// order and row ids from the markers in each first cell; a column or row without one gets a new id. Missing cells
// are filled with empty text and extra cells beyond the header are kept by widening the table.
internal object TableReader {
    data class Result(
        val table: TableBlock,
        val next: Int,
    )

    fun read(
        lines: List<String>,
        start: Int,
        id: BlockId,
        declaredColumns: List<String>,
    ): Result {
        var index = start + 2
        val rows = arrayListOf(TableSyntax.cells(lines[start]))
        while (index < lines.size && TableSyntax.isRow(lines[index])) {
            rows += TableSyntax.cells(lines[index])
            index++
        }
        val alignments = TableSyntax.cells(lines[start + 1]).map(TableSyntax::alignment)
        val width = rows.maxOf { it.size }
        val columns = columns(width, declaredColumns, alignments)
        return Result(TableBlock.of(id, columns, tableRows(rows, width)), index)
    }

    private fun columns(
        width: Int,
        declared: List<String>,
        alignments: List<ColumnAlignment>,
    ): List<TableColumn> {
        val ids = IdClaims()
        return (0 until width).map { position ->
            TableColumn(ids.claim(declared.getOrNull(position)), alignments.getOrElse(position) { ColumnAlignment.NONE })
        }
    }

    private fun tableRows(
        rows: List<List<String>>,
        width: Int,
    ): List<TableRow> {
        val ids = IdClaims()
        return rows.map { cells ->
            val (marker, first) = TableSyntax.rowMarker(cells.first())
            val texts = (listOf(first) + cells.drop(1)).map(::InlineText)
            TableRow.of(ids.claim(marker), texts + List(width - texts.size) { InlineText.empty })
        }
    }

    private class IdClaims {
        private val used = HashSet<BlockId>()
        private val pattern = Regex("[a-z0-9]{4,16}")

        fun claim(declared: String?): BlockId {
            var id = declared?.takeIf(pattern::matches)?.let(::BlockId)?.takeIf { it !in used } ?: BlockId.next()
            while (id in used) {
                id = BlockId.next()
            }
            used += id
            return id
        }
    }
}
