package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.FlowQuery
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.port.output.MarkdownTokenCounter
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.DocumentMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// Both transports page exactly the same canonical text. Cursors never cut a UTF-16 surrogate pair. publicUrl, when set,
// turns flow query candidates into links the web app opens on the same screen and element.
internal class MarkdownPages(
    private val tokens: MarkdownTokenCounter,
    private val publicUrl: String? = null,
) {
    fun chunk(
        document: Document,
        space: SpaceSlug,
        request: MarkdownRequest,
    ): MarkdownChunk {
        request.flowQuery?.let { return targets(document, space, request, it) }
        val selected = select(document, request)
        return page(document, bodyParts(document, space, request.format, selected), request.cursor, selected.size)
    }

    private fun targets(
        document: Document,
        space: SpaceSlug,
        request: MarkdownRequest,
        query: FlowQuery,
    ): MarkdownChunk {
        if (document.header.type != "flow") {
            throw InvalidRequestException("flowQuery is only for flow documents", "read this document without flowQuery")
        }
        val links = if (request.revision == null) linkTo(document, space) else null
        val targets = FlowTargets.list(document, query, links)
        return page(document, targets.parts, request.cursor, targets.matches)
    }

    private fun bodyParts(
        document: Document,
        space: SpaceSlug,
        format: MarkdownFormat,
        selected: List<Block>,
    ): Sequence<String> =
        when {
            format != MarkdownFormat.OUTLINE -> DocumentMarkdown.parts(document, space, format == MarkdownFormat.ANNOTATED, selected)
            document.header.type == "flow" && document.blocks.firstOrNull() is FlowSettings -> FlowOutline.parts(document, selected)
            else -> outlineParts(selected)
        }

    private fun page(
        document: Document,
        parts: Sequence<String>,
        cursor: Int,
        total: Int,
    ): MarkdownChunk {
        val window = windowOf(parts, cursor)
        if (window.firstOrNull()?.isLowSurrogate() == true) invalidCursor()
        val end = fittingEnd(window)
        val next = (cursor + end).takeIf { end < window.length }
        return MarkdownChunk(document, window.substring(0, end), next, total)
    }

    // The query names the stored block ids, so the link still opens the same targets after reordering or renaming.
    // Space slugs, document UUIDs and block ids only use URL-safe characters, so nothing needs encoding.
    private fun linkTo(
        document: Document,
        space: SpaceSlug,
    ): ((FlowScreen, FlowElement?) -> String)? {
        val base = publicUrl?.trimEnd('/') ?: return null
        val path = "$base/s/${space.value}/d/${document.id.value}"
        return { screen, element -> "$path?flowScreen=${screen.id}" + element?.let { "&flowElement=${it.id}" }.orEmpty() }
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
        val selected = document.blocks.getOrNull(start)
        if (selected is FlowScreen) {
            return listOf(selected) +
                document.blocks.drop(start + 1).takeWhile { it is FlowElement }
        }
        if (selected is FlowDesignBlock && selected.kind == "screen") {
            return listOf(selected) +
                document.blocks.filterIsInstance<FlowDesignBlock>().filter { it.string("screen") == selected.designId }
        }
        val heading = selected as? Heading ?: missing(headingId)
        val rest = document.blocks.drop(start + 1)
        val end = rest.indexOfFirst { it is Heading && it.level <= heading.level }.let { if (it < 0) rest.size else it }
        return listOf(heading) + rest.take(end)
    }

    private fun outline(block: Block): String {
        val markdown = BlockMarkdown.of(block)
        val hash = BlockMarkdown.hash(block, markdown)
        if (block is Heading) return "${preview(markdown)} (b:${block.id} h:$hash ${markdown.length}자)"
        if (block is FlowDesignBlock) {
            val label = listOfNotNull(block.string("code"), block.string("name")).joinToString(" ")
            return "- ${block.kind} ${block.designId} ${preview(label)} (b:${block.id} h:$hash ${markdown.length}자)"
        }
        val preview = preview(block.plainText())
        val type =
            block::class
                .simpleName
                .orEmpty()
                .removeSuffix("Block")
                .lowercase()
        return "- $type b:${block.id} h:$hash ${markdown.length}자: $preview"
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
