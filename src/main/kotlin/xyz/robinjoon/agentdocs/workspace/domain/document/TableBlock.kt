package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.Collections

// A GFM table. The first row is the header. Rows and columns carry their own ids, so a cell comment is addressed as
// table id + row id + column id and survives sorting and inserting rows or columns. Cell merging is not supported
// because markdown cannot express it.
@ConsistentCopyVisibility
data class TableBlock private constructor(
    override val id: BlockId,
    val columns: List<TableColumn>,
    val rows: List<TableRow>,
) : Block {
    init {
        if (columns.size !in 1..MAX_COLUMNS || rows.size !in 1..MAX_ROWS) {
            throw InvalidDocumentException("a table has 1 to $MAX_COLUMNS columns and 1 to $MAX_ROWS rows")
        }
        if (rows.any { it.cells.size != columns.size }) {
            throw InvalidDocumentException("every row needs one cell per column")
        }
        if (columns.map { it.id }.toSet().size != columns.size || rows.map { it.id }.toSet().size != rows.size) {
            throw InvalidDocumentException("row and column ids must be unique within a table")
        }
    }

    fun row(id: BlockId): TableRow? = rows.firstOrNull { it.id == id }

    fun columnIndex(id: BlockId): Int = columns.indexOfFirst { it.id == id }

    override fun withId(id: BlockId): Block = copy(id = id)

    // Plain markdown carries no column ids, and rows lose theirs when their markers are left out, so a rewritten table
    // gets fresh ones. When none of them are the earlier table's, the earlier ids come back by position and cell
    // comments keep their place. Ids the writer did send are kept as they are.
    fun keepingIdsOf(earlier: TableBlock): TableBlock {
        val earlierColumns = earlier.columns.map { it.id }.toSet()
        val earlierRows = earlier.rows.map { it.id }.toSet()
        val keptColumns =
            if (columns.any { it.id in earlierColumns }) {
                columns
            } else {
                columns.mapIndexed { index, column ->
                    earlier.columns.getOrNull(index)?.let { column.copy(id = it.id) }
                        ?: column
                }
            }
        val keptRows =
            if (rows.any { it.id in earlierRows }) {
                rows
            } else {
                rows.mapIndexed { index, row ->
                    earlier.rows.getOrNull(index)?.let { TableRow.of(it.id, row.cells) }
                        ?: row
                }
            }
        return of(id, keptColumns, keptRows)
    }

    override fun plainText(): String = rows.joinToString("\n") { row -> row.cells.joinToString("\t") { it.plainText() } }

    companion object {
        const val MAX_COLUMNS = 50
        const val MAX_ROWS = 1000

        fun of(
            id: BlockId,
            columns: List<TableColumn>,
            rows: List<TableRow>,
        ): TableBlock = TableBlock(id, Collections.unmodifiableList(columns.toList()), Collections.unmodifiableList(rows.toList()))
    }
}
