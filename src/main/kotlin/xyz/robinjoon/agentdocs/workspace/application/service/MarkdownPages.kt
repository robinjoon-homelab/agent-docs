package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.DocumentMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// Cuts a document into responses an agent can take in. The front matter comes with the first chunk only, and a chunk
// never splits a block, so every chunk is valid markdown with whole annotated blocks.
internal object MarkdownPages {
    // About 20,000 tokens for mixed Korean and English text.
    const val CHUNK_CHARACTERS = 40_000
    private const val PREVIEW_LENGTH = 60

    fun chunk(
        document: Document,
        space: SpaceSlug,
        request: MarkdownRequest,
    ): MarkdownChunk {
        val selected = select(document, request)
        val pieces = selected.drop(request.cursor).map { render(it, request.format) }
        val head = if (request.cursor == 0 && request.format != MarkdownFormat.OUTLINE) frontMatterOf(document, space) else ""
        val taken = pieces.take(fitting(pieces, head.length))
        val next = (request.cursor + taken.size).takeIf { it < selected.size }
        return MarkdownChunk(document, head + taken.joinToString("\n\n") + "\n", next, selected.size)
    }

    // How many pieces fit after the head. The first piece always goes in, so a single huge block still makes progress.
    private fun fitting(
        pieces: List<String>,
        head: Int,
    ): Int {
        var used = head
        var count = 0
        for (piece in pieces) {
            if (count > 0 && used + piece.length > CHUNK_CHARACTERS) break
            used += piece.length + 2
            count++
        }
        return count
    }

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

    private fun render(
        block: Block,
        format: MarkdownFormat,
    ): String =
        when (format) {
            MarkdownFormat.ANNOTATED -> BlockMarkdown.annotation(block) + "\n" + BlockMarkdown.of(block)
            MarkdownFormat.PLAIN -> BlockMarkdown.of(block)
            MarkdownFormat.OUTLINE -> outline(block)
        }

    private fun outline(block: Block): String {
        val markdown = BlockMarkdown.of(block)
        if (block is Heading) return "$markdown (b:${block.id} h:${BlockMarkdown.hash(block)} ${markdown.length}자)"
        val preview = block.plainText().replace('\n', ' ').take(PREVIEW_LENGTH)
        val type =
            block::class
                .simpleName
                .orEmpty()
                .removeSuffix("Block")
                .lowercase()
        return "- $type b:${block.id} h:${BlockMarkdown.hash(block)} ${markdown.length}자: $preview"
    }

    private fun frontMatterOf(
        document: Document,
        space: SpaceSlug,
    ): String {
        val whole = DocumentMarkdown.write(document.copyWithoutBlocks(), space, annotated = false)
        return whole.trimEnd() + "\n\n"
    }

    private fun Document.copyWithoutBlocks(): Document = Document.restore(id, header, emptyList(), lifecycle)

    private fun missing(id: String): Nothing =
        throw InvalidRequestException("block $id is not in the document", "read the outline to find block ids")
}
