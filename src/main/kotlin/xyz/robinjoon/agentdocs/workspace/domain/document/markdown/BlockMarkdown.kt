package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Callout
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.Divider
import xyz.robinjoon.agentdocs.workspace.domain.document.DrawingBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.ListStyle
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.Quote
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import java.security.MessageDigest

// Writes each block in its single canonical markdown form. The same blocks always give the same bytes, which is what
// makes the block hash a reliable change detector and keeps revisions comparable as text.
object BlockMarkdown {
    private const val HASH_LENGTH = 8
    private const val BYTE_MASK = 0xff
    private const val HEX_RADIX = 16
    private val leadingSyntax = Regex("^(#{1,6}\\s|>|[-*+]\\s|```|~~~|\\||<!--|!\\[|(-{3,}|\\*{3,}|_{3,})\\s*$)")
    private val leadingNumber = Regex("^(\\d+)([.)])(\\s)")
    private val longFence = Regex("`{3,}")
    private val escapedNumber = Regex("^(\\d+)\\\\([.)])(\\s)")
    private val taskLike = Regex("^\\[[ xX]]\\s")

    // Blocks are separated by one blank line, except that adjacent list items stay on adjacent lines so the list
    // renders tight. Annotated output puts "<!-- b:id h:hash -->" before every block.
    fun write(
        blocks: List<Block>,
        annotated: Boolean,
    ): String = parts(blocks, annotated).joinToString("")

    fun forExport(
        blocks: List<Block>,
        pathOf: (AssetId) -> String?,
    ): String = parts(blocks, annotated = false, pathOf).joinToString("")

    internal fun parts(
        blocks: List<Block>,
        annotated: Boolean,
        pathOf: ((AssetId) -> String?)? = null,
    ): Sequence<String> =
        blocks.asSequence().mapIndexed { index, block ->
            val separator = if (index == 0) "" else separatorBefore(block, blocks[index - 1])
            separator + (if (annotated) annotation(block) + "\n" else "") + render(block, pathOf)
        }

    private fun separatorBefore(
        block: Block,
        previous: Block,
    ): String = if (block is ListItem && previous is ListItem) "\n" else "\n\n"

    fun annotation(block: Block): String {
        val columns = if (block is TableBlock) " cols:" + block.columns.joinToString(",") { it.id.value } else ""
        return "<!-- b:${block.id} h:${hash(block)}$columns -->"
    }

    // The first 8 hex digits of the SHA-256 of the canonical markdown, including row and column ids for tables.
    fun hash(block: Block): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((of(block) + columnsOf(block)).toByteArray())
        return digest.joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }.take(HASH_LENGTH)
    }

    fun of(block: Block): String = render(block, null)

    private fun render(
        block: Block,
        pathOf: ((AssetId) -> String?)?,
    ): String =
        when (block) {
            is FlowSettings, is FlowScreen, is FlowElement -> FlowMarkdown.write(block)
            is Heading -> "#".repeat(block.level) + " " + block.text.render(pathOf)
            is Paragraph -> escapeLeading(block.text.render(pathOf))
            is ListItem -> listItem(block, block.text.render(pathOf))
            is Quote -> "> " + escapeQuote(block.text.render(pathOf))
            is Callout -> "> [!${block.kind}]\n> " + block.text.render(pathOf)
            is CodeBlock -> code(block)
            is TableBlock -> table(block, pathOf)
            is ImageBlock -> image(block.alt, destination(block.asset, pathOf), block.caption)
            is DrawingBlock -> image(block.alt, destination(block.svg, pathOf), "excalidraw " + destination(block.scene, pathOf))
            is Divider -> "---"
        }

    private fun InlineText.render(pathOf: ((AssetId) -> String?)?): String =
        if (pathOf == null || "asset:" !in markdown) markdown else InlineMarkdown.rewriteAssetLinks(markdown, pathOf)

    private fun destination(
        asset: AssetId,
        pathOf: ((AssetId) -> String?)?,
    ): String = pathOf?.invoke(asset) ?: "asset:$asset"

    private fun columnsOf(block: Block): String = if (block is TableBlock) block.columns.joinToString(",") { it.id.value } else ""

    private fun listItem(
        item: ListItem,
        content: String,
    ): String {
        val marker = if (item.style == ListStyle.NUMBERED) "1." else "-"
        val task =
            if (item.style != ListStyle.TASK) {
                ""
            } else if (item.checked) {
                "[x] "
            } else {
                "[ ] "
            }
        val text = if (item.style == ListStyle.TASK) content else escapeTaskLike(content)
        return "  ".repeat(item.depth) + marker + " " + task + text
    }

    private fun code(block: CodeBlock): String {
        if (block.displayMath) return "$$\n${block.code}\n$$"
        val longest = longFence.findAll(block.code).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longest + 1))
        val body = if (block.code.isEmpty()) "" else block.code + "\n"
        return fence + block.language + "\n" + body + fence
    }

    private fun table(
        block: TableBlock,
        pathOf: ((AssetId) -> String?)?,
    ): String {
        val lines =
            block.rows.map { row ->
                "| <!--r:${row.id}-->" + row.cells.joinToString(" | ") { TableSyntax.escape(it.render(pathOf)) } +
                    " |"
            }
        val delimiter = block.columns.joinToString(" | ", "| ", " |") { TableSyntax.delimiter(it.alignment) }
        return (listOf(lines.first(), delimiter) + lines.drop(1)).joinToString("\n")
    }

    private fun image(
        label: String,
        destination: String,
        title: String,
    ): String {
        val alt = label.replace("\\", "\\\\").replace("]", "\\]")
        val caption = if (title.isEmpty()) "" else " \"" + title.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        return "![$alt]($destination$caption)"
    }

    // The reader undoes exactly the escape the writer added, so reading written markdown gives back the same text.
    internal fun paragraphText(written: String): String {
        val candidate = if (written.startsWith("\\")) written.drop(1) else written.replaceFirst(escapedNumber, "$1$2$3")
        return if (candidate != written && escapeLeading(candidate) == written) candidate else written
    }

    internal fun quoteText(written: String): String = unescapeFirst(written, ::escapeQuote)

    internal fun listItemText(written: String): String = unescapeFirst(written, ::escapeTaskLike)

    private fun unescapeFirst(
        written: String,
        escape: (String) -> String,
    ): String = if (written.startsWith("\\") && escape(written.drop(1)) == written) written.drop(1) else written

    // A paragraph that would read as another block keeps its text by escaping the leading marker.
    private fun escapeLeading(text: String): String =
        when {
            text == "$$" -> "\\" + text
            leadingNumber.containsMatchIn(text) -> text.replaceFirst(leadingNumber, "$1\\\\$2$3")
            leadingSyntax.containsMatchIn(text) -> "\\" + text
            else -> text
        }

    private fun escapeQuote(text: String): String = if (text.startsWith("[!")) "\\" + text else text

    private fun escapeTaskLike(text: String): String = if (taskLike.containsMatchIn(text)) "\\" + text else text
}
