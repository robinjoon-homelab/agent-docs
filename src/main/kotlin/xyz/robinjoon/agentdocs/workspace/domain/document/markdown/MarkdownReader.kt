package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Callout
import xyz.robinjoon.agentdocs.workspace.domain.document.CalloutKind
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.Divider
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.ListStyle
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.Quote
import java.util.UUID

// Reads the markdown the platform writes, plus the plain GFM an agent writes without annotations. An annotation
// keeps the id of the block that follows it; a block without one gets a new id. A repeated id is treated as a copy and
// also gets a new id, so the result always has unique ids.
class MarkdownReader private constructor(
    private val lines: List<String>,
) {
    private var index = 0
    private var annotation: Annotation? = null
    private val blocks = ArrayList<ParsedBlock>()
    private val usedIds = HashSet<BlockId>()

    private fun read(withFrontMatter: Boolean): ParsedMarkdown {
        val frontMatter = if (withFrontMatter) readFrontMatter() else FrontMatter.empty
        while (index < lines.size) {
            readNext()
        }
        return ParsedMarkdown(frontMatter, blocks)
    }

    private fun readNext() {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            index++
            return
        }
        val parsed = Annotation.parse(trimmed)
        if (parsed != null) {
            annotation = parsed
            index++
            return
        }
        val note = annotation
        annotation = null
        blocks += ParsedBlock(readBlock(line, note), note?.hash)
    }

    private fun readBlock(
        line: String,
        note: Annotation?,
    ): Block {
        val id = claimId(note?.id)
        val trimmed = line.trim()
        return when {
            fencePattern.matches(trimmed) -> readCode(id)
            startsTable(index) -> TableReader.read(lines, index, id, note?.columns.orEmpty()).also { index = it.next }.table
            headingPattern.matches(line) -> readHeading(id)
            dividerPattern.matches(line) -> Divider(id).also { index++ }
            calloutPattern.matches(trimmed) -> readCallout(id)
            trimmed.startsWith(">") -> Quote(id, InlineText(BlockMarkdown.quoteText(quotedLines().joinToString(LINE_BREAK))))
            trimmed.startsWith("![") -> readImage(id, trimmed)
            listItemPattern.matches(line) -> readListItem(id)
            else -> Paragraph(id, InlineText(BlockMarkdown.paragraphText(paragraphLines().joinToString(" "))))
        }
    }

    private fun claimId(declared: BlockId?): BlockId {
        var id = declared?.takeIf { it !in usedIds } ?: BlockId.next()
        while (id in usedIds) {
            id = BlockId.next()
        }
        usedIds += id
        return id
    }

    private fun readFrontMatter(): FrontMatter {
        if (lines.firstOrNull()?.trim() != FENCE_FRONT_MATTER) return FrontMatter.empty
        val close = lines.drop(1).indexOfFirst { it.trim() == FENCE_FRONT_MATTER } + 1
        val body = if (close > 0) lines.subList(1, close) else return FrontMatter.empty
        if (!body.all { it.isBlank() || frontMatterEntryPattern.matches(it) }) return FrontMatter.empty
        index = close + 1
        return FrontMatter(body.filter(String::isNotBlank).map { it.substringBefore(':').trim() to it.substringAfter(':').trim() })
    }

    private fun readCode(id: BlockId): CodeBlock {
        val opening = requireNotNull(fencePattern.matchEntire(lines[index].trim()))
        val fence = opening.groupValues[1]
        val body = ArrayList<String>()
        index++
        while (index < lines.size && !isClosingFence(lines[index], fence)) {
            body += lines[index]
            index++
        }
        index++
        return CodeBlock(id, opening.groupValues[2], body.joinToString("\n"))
    }

    private fun isClosingFence(
        line: String,
        fence: String,
    ): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= fence.length && trimmed.all { it == fence.first() }
    }

    private fun readHeading(id: BlockId): Heading {
        val match = requireNotNull(headingPattern.matchEntire(lines[index]))
        index++
        return Heading(id, minOf(match.groupValues[1].length, Heading.MAX_LEVEL), InlineText(match.groupValues[2].trim()))
    }

    private fun readCallout(id: BlockId): Callout {
        val kind = CalloutKind.valueOf(requireNotNull(calloutPattern.matchEntire(lines[index].trim())).groupValues[1])
        index++
        val text = quotedLines().joinToString(LINE_BREAK)
        if (text.isBlank()) {
            throw InvalidDocumentException("a > [!$kind] callout needs at least one > line of text below it")
        }
        return Callout(id, kind, InlineText(text))
    }

    private fun quotedLines(): List<String> {
        val quoted = ArrayList<String>()
        while (index < lines.size && lines[index].trim().startsWith(">")) {
            quoted += lines[index].trim().removePrefix(">").trim()
            index++
        }
        return quoted.filter(String::isNotEmpty)
    }

    private fun readImage(
        id: BlockId,
        line: String,
    ): ImageBlock {
        val match =
            imagePattern.matchEntire(line) ?: throw InvalidDocumentException(
                "images must reference an uploaded asset as ![alt](asset:ID \"caption\"); upload the file first and use its asset id",
            )
        index++
        val asset = AssetId(UUID.fromString(match.groupValues[2]))
        return ImageBlock(id, asset, unescape(match.groupValues[1]), unescape(match.groupValues[3]))
    }

    private fun readListItem(id: BlockId): ListItem {
        val match = requireNotNull(listItemPattern.matchEntire(lines[index]))
        index++
        val (indent, marker, box, text) = match.destructured
        val style =
            when {
                box.isNotEmpty() -> ListStyle.TASK
                marker.first().isDigit() -> ListStyle.NUMBERED
                else -> ListStyle.BULLET
            }
        val depth = minOf(indent.replace("\t", "    ").length / INDENT_WIDTH, ListItem.MAX_DEPTH)
        val content = if (style == ListStyle.TASK) text.trim() else BlockMarkdown.listItemText(text.trim())
        return ListItem(id, style, depth, box.trim().equals("[x]", ignoreCase = true), InlineText(content))
    }

    private fun paragraphLines(): List<String> {
        val collected = arrayListOf(lines[index].trim())
        index++
        while (index < lines.size && !startsBlock(lines[index])) {
            collected += lines[index].trim()
            index++
        }
        return collected
    }

    private fun startsBlock(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.isEmpty() || Annotation.parse(trimmed) != null || fencePattern.matches(trimmed) || startsTable(index) ||
            headingPattern.matches(line) || dividerPattern.matches(line) || trimmed.startsWith(">") || trimmed.startsWith("![") ||
            listItemPattern.matches(line)
    }

    private fun startsTable(at: Int): Boolean =
        TableSyntax.isRow(lines[at]) && lines.getOrNull(at + 1)?.let(TableSyntax::isDelimiterRow) == true

    private fun unescape(text: String): String = text.replace(escapedPattern, "$1")

    companion object {
        private const val LINE_BREAK = "<br>"
        private const val FENCE_FRONT_MATTER = "---"
        private const val INDENT_WIDTH = 2
        private val fencePattern = Regex("(`{3,}|~{3,})\\s*([^\\s`]*)\\s*")
        private val headingPattern = Regex("\\s{0,3}(#{1,6})\\s+(\\S.*?)\\s*")
        private val dividerPattern = Regex("\\s{0,3}(-{3,}|\\*{3,}|_{3,})\\s*")
        private val calloutPattern = Regex(">\\s*\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*")
        private val imagePattern = Regex("!\\[((?:\\\\.|[^\\]\\\\])*)]\\(asset:([0-9a-fA-F-]{36})(?:\\s+\"((?:\\\\.|[^\"\\\\])*)\")?\\)")
        private val listItemPattern = Regex("(\\s*)([-*+]|\\d+[.)])\\s+(\\[[ xX]]\\s+)?(.*)")
        private val frontMatterEntryPattern = Regex("[A-Za-z_][A-Za-z0-9_-]*:.*")
        private val escapedPattern = Regex("\\\\(.)")

        // A whole document: optional front matter, then blocks.
        fun read(markdown: String): ParsedMarkdown = MarkdownReader(lines(markdown)).read(withFrontMatter = true)

        // A fragment such as the blocks of one edit operation. A leading "---" is a divider here, not front matter.
        fun readBlocks(markdown: String): List<ParsedBlock> = MarkdownReader(lines(markdown)).read(withFrontMatter = false).blocks

        private fun lines(markdown: String): List<String> = markdown.replace("\r\n", "\n").split('\n')
    }
}
