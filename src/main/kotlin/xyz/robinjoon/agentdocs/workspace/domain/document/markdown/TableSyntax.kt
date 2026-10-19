package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.ColumnAlignment

// The GFM delimiter row: "---", ":--", ":-:", or "--:" per column.
internal object TableSyntax {
    private val delimiterCell = Regex(":?-+:?")
    private val unescapedPipe = Regex("(?<!\\\\)\\|")
    private const val ROW_MARKER_PREFIX = "<!--r:"
    private const val ROW_MARKER_SUFFIX = "-->"

    fun delimiter(alignment: ColumnAlignment): String =
        when (alignment) {
            ColumnAlignment.NONE -> "---"
            ColumnAlignment.LEFT -> ":--"
            ColumnAlignment.CENTER -> ":-:"
            ColumnAlignment.RIGHT -> "--:"
        }

    fun alignment(cell: String): ColumnAlignment =
        when {
            cell.startsWith(":") && cell.endsWith(":") -> ColumnAlignment.CENTER
            cell.startsWith(":") -> ColumnAlignment.LEFT
            cell.endsWith(":") -> ColumnAlignment.RIGHT
            else -> ColumnAlignment.NONE
        }

    fun isRow(line: String): Boolean = line.trimStart().startsWith("|")

    fun isDelimiterRow(line: String): Boolean =
        isRow(line) && cells(line).let { cells -> cells.isNotEmpty() && cells.all(delimiterCell::matches) }

    // Splits on unescaped pipes. The outer pipes are optional in GFM; the platform always writes them.
    fun cells(line: String): List<String> {
        val inner = line.trim().removePrefix("|").let { if (it.endsWith("|") && !it.endsWith("\\|")) it.dropLast(1) else it }
        return inner.split(unescapedPipe).map(String::trim)
    }

    // "<!--r:abc123-->rest" at the start of a row's first cell carries the row id.
    fun rowMarker(cell: String): Pair<String?, String> {
        if (!cell.startsWith(ROW_MARKER_PREFIX)) return null to cell
        val end = cell.indexOf(ROW_MARKER_SUFFIX)
        if (end < 0) return null to cell
        return cell.substring(ROW_MARKER_PREFIX.length, end) to cell.substring(end + ROW_MARKER_SUFFIX.length).trim()
    }
}
