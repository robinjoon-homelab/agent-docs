package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.Wire
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceJson
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListPosition
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceGuide
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class DocsSearchTool(
    private val search: SearchUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_search",
            title = "Search documents",
            description =
                "Search titles, block text, and comments. Defaults to the only accessible space. " +
                    "With multiple spaces, specify space or set allSpaces=true.",
            properties =
                mapOf(
                    "query" to Schemas.string("Text to find, 1 to 100 characters"),
                    "space" to Schemas.string("Search this space by slug"),
                    "allSpaces" to Schemas.boolean("Search every accessible space, including archived ones. Use without space."),
                ),
            required = listOf("query"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val space = if (arguments.node.has("space")) SpaceSlug(arguments.string("space")) else null
        val hits = search.search(context.access, arguments.string("query"), space, allSpaces = arguments.boolean("allSpaces"))
        return McpToolResult.data(
            mapOf(
                "hits" to
                    hits.map {
                        mapOf(
                            "kind" to it.kind.name.lowercase(),
                            "space" to it.space.slug.value,
                            "documentId" to it.document.id.value,
                            "path" to it.document.path.value,
                            "title" to it.document.title,
                            "type" to it.document.type,
                            "blockId" to it.blockId?.value,
                            "threadId" to it.threadId?.value,
                            "snippet" to it.snippet,
                        )
                    },
            ),
        )
    }
}

class DocsListTool(
    private val queries: DocumentQueryUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_list",
            title = "List documents",
            description =
                "Documents of a space, most recently changed first. The leading segments of a path are the parent document, so " +
                    "folder lists the documents under a document. For more results pass nextCursor back as cursor.",
            properties =
                mapOf(
                    "space" to Schemas.string("Space slug"),
                    "folder" to Schemas.string("Path of a parent document such as payments: lists the documents under it"),
                    "status" to Schemas.choice("Document status", listOf("draft", "in_review", "confirmed", "archived")),
                    "tag" to Schemas.string("Tag"),
                    "trashed" to Schemas.boolean("List the trash instead"),
                    "cursor" to Schemas.string("nextCursor from the previous page"),
                ),
            required = listOf("space"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val status = arguments.optionalString("status")?.let(Wire::status)
        val filter =
            DocumentFilter(
                status,
                arguments.optionalString("tag"),
                arguments.optionalString("folder"),
                arguments.boolean("trashed"),
                arguments.optionalString("cursor")?.let(DocumentListPosition::read),
            )
        val page = queries.list(context.access, SpaceSlug(arguments.string("space")), filter)
        return McpToolResult.data(mapOf("documents" to page.documents.map(WorkspaceJson::listing), "nextCursor" to page.nextCursor))
    }
}

// The main way an agent reads. Annotated markdown names every block's id and hash, which docs_edit needs. The first
// chunk of a document whose space names an agent guide carries a second text block pointing to it; the document
// markdown itself stays byte for byte what the version serializes to.
class DocsReadTool(
    private val queries: DocumentQueryUseCase,
    private val spaces: SpaceUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_read",
            title = "Read a document",
            description =
                "Reads a document as markdown. format markdown (default) puts <!-- b:ID h:HASH --> before every block; use those with " +
                    "docs_edit. " + FlowToolHelp.SYNTAX +
                    "plain has no annotations; outline lists headings and block ids with sizes. Large documents come in chunks: pass " +
                    "cursor to continue.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "format" to Schemas.choice("Output format", listOf("markdown", "plain", "outline")),
                    "section" to Schemas.string("Heading block id: read that section only"),
                    "blocks" to Schemas.strings("Block ids: read only these blocks"),
                    "cursor" to Schemas.integer("nextCursor in the previous result metadata"),
                    "revision" to Schemas.integer("Confirmed revision to read instead of the current document"),
                ),
            required = listOf("document"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val request =
            MarkdownRequest(
                Wire.markdownFormat(arguments.optionalString("format")),
                arguments.optionalString("section")?.let(::BlockId),
                arguments.strings("blocks")?.map(::BlockId),
                arguments.optionalInt("cursor") ?: 0,
                arguments.optionalInt("revision"),
            )
        val chunk = queries.markdown(context.access, DocumentId(arguments.uuid("document")), request)
        val more = chunk.nextCursor?.let { "Continue docs_read with nextCursor $it (${chunk.totalBlocks} blocks in total)." }
        val guide = if (request.cursor == 0) guideNotes(context, chunk.document) else emptyList()
        val notes = guide + listOfNotNull(more)
        val metadata =
            chunk.nextCursor?.let {
                mapOf(
                    "documentId" to chunk.document.id.value,
                    "version" to chunk.document.version,
                    "revision" to chunk.document.revision,
                    "totalBlocks" to chunk.totalBlocks,
                    "nextCursor" to it,
                )
            }
        return McpToolResult.markdown(chunk.text, notes, metadata)
    }

    // Reading the guide itself needs no pointer to it.
    private fun guideNotes(
        context: WriteContext,
        document: Document,
    ): List<String> {
        val guide = spaces.agentGuide(context.access, document.spaceId) ?: return emptyList()
        return if (guide.documentId == document.id) emptyList() else listOf(noteOf(guide))
    }

    private fun noteOf(guide: SpaceGuide.Offered): String =
        "This space's agent guide is [${guide.title}](doc:${guide.documentId}). Read it with docs_read before you write in this " +
            "space, and follow its writing rules, terms, and templates."
}
