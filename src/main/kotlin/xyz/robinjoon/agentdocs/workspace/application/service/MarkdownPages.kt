package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.port.output.MarkdownTokenCounter
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.DocumentMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// Both transports page exactly the same canonical text. Cursors never cut a UTF-16 surrogate pair.
internal class MarkdownPages(
    private val tokens: MarkdownTokenCounter,
) {
    fun chunk(
        document: Document,
        space: SpaceSlug,
        request: MarkdownRequest,
    ): MarkdownChunk {
        val selected = select(document, request)
        val parts =
            if (request.format == MarkdownFormat.OUTLINE) {
                outlineParts(selected)
            } else {
                DocumentMarkdown.parts(document, space, request.format == MarkdownFormat.ANNOTATED, selected)
            }
        val window = windowOf(parts, request.cursor)
        if (window.firstOrNull()?.isLowSurrogate() == true) invalidCursor()
        val end = fittingEnd(window)
        val next = (request.cursor + end).takeIf { end < window.length }
        return MarkdownChunk(document, window.substring(0, end), next, selected.size)
    }

    private fun outlineParts(blocks: List<Block>): Sequence<String> =
        blocks.asSequence().mapIndexed { index, block ->
            (if (index == 0) "" else "\n\n") + outline(block)
        } + sequenceOf("\n")

    private fun windowOf(
        parts: Sequence<String>,
        cursor: Int,
    ): String {
        var skip = cursor
        val window = StringBuilder()
        for (part in parts) {
            if (skip >= part.length) {
                skip -= part.length
                continue
            }
            val taken = minOf(part.length - skip, MAX_LOOKAHEAD + 1 - window.length)
            window.append(part, skip, skip + taken)
            skip = 0
            if (window.length > MAX_LOOKAHEAD) break
        }
        if (skip > 0) invalidCursor()
        return window.toString()
    }

    private fun fittingEnd(window: String): Int {
        val suffix = window.substring(0, boundary(window, minOf(window.length, MAX_LOOKAHEAD)))
        if (tokens.count(suffix) <= TOKEN_LIMIT) return suffix.length
        var low = 1
        var high = suffix.length
        while (low < high) {
            val middle = (low + high + 1) / 2
            val candidate = suffix.substring(0, boundary(suffix, middle))
            if (tokens.count(candidate) <= TOKEN_LIMIT) low = middle else high = middle - 1
        }
        return boundary(suffix, low)
    }

    private fun boundary(
        text: String,
        next: Int,
    ): Int = if (next < text.length && text[next].isLowSurrogate()) next - 1 else next

    private fun invalidCursor(): Nothing = throw InvalidRequestException("Use the next cursor from the previous Markdown page")

    private fun select(
        document: Document,
        request: MarkdownRequest,
    ): List<Block> =
        when {
            request.blocks != null -> request.blocks.map { id -> document.block(id) ?: missing(id.value) }
            request.section != null -> section(document, request.section.value)
            else -> document.blocks
        }

    // A heading and everything after it up to the next heading of the same or a higher level.
    private fun section(
        document: Document,
        headingId: String,
    ): List<Block> {
        val start = document.blocks.indexOfFirst { it.id.value == headingId }
        val heading = document.blocks.getOrNull(start) as? Heading ?: missing(headingId)
        val rest = document.blocks.drop(start + 1)
        val end = rest.indexOfFirst { it is Heading && it.level <= heading.level }.let { if (it < 0) rest.size else it }
        return listOf(heading) + rest.take(end)
    }

    private fun outline(block: Block): String {
        val markdown = BlockMarkdown.of(block)
        if (block is Heading) return "${preview(markdown)} (b:${block.id} h:${BlockMarkdown.hash(block)} ${markdown.length}자)"
        val preview = preview(block.plainText())
        val type =
            block::class
                .simpleName
                .orEmpty()
                .removeSuffix("Block")
                .lowercase()
        return "- $type b:${block.id} h:${BlockMarkdown.hash(block)} ${markdown.length}자: $preview"
    }

    private fun preview(text: String): String = text.substring(0, boundary(text, minOf(text.length, PREVIEW_LENGTH))).replace('\n', ' ')

    private fun missing(id: String): Nothing =
        throw InvalidRequestException("block $id is not in the document", "read the outline to find block ids")

    private companion object {
        const val TOKEN_LIMIT = 20_000
        const val MAX_LOOKAHEAD = TOKEN_LIMIT * 8
        const val PREVIEW_LENGTH = 60
    }
}
