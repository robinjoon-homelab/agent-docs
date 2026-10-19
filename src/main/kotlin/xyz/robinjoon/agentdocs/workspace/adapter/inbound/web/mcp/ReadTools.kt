package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.Wire
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceJson
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class DocsSearchTool(
    private val search: SearchUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_search",
            title = "Search documents",
            description = "Substring search over titles, block text, and comments. One space by slug, or every space this connection sees.",
            properties =
                mapOf(
                    "query" to Schemas.string("Text to find, 1 to 100 characters"),
                    "space" to Schemas.string("Space slug; omit for all spaces"),
                ),
            required = listOf("query"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val hits = search.search(context.access, arguments.string("query"), arguments.optionalString("space")?.let(::SpaceSlug))
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
                "Documents of a space, most recently changed first. Paths form the folder tree; filter by folder, status, or tag.",
            properties =
                mapOf(
                    "space" to Schemas.string("Space slug"),
                    "folder" to Schemas.string("Folder path prefix such as payments"),
                    "status" to Schemas.choice("Document status", listOf("draft", "in_review", "confirmed", "archived")),
                    "tag" to Schemas.string("Tag"),
                    "trashed" to Schemas.boolean("List the trash instead"),
                ),
            required = listOf("space"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val status =
            arguments.optionalString("status")?.let { value ->
                DocumentStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            }
        val filter =
            DocumentFilter(status, arguments.optionalString("tag"), arguments.optionalString("folder"), arguments.boolean("trashed"))
        val listings = queries.list(context.access, SpaceSlug(arguments.string("space")), filter)
        return McpToolResult.data(mapOf("documents" to listings.map(WorkspaceJson::listing)))
    }
}

// The main way an agent reads. Annotated markdown names every block's id and hash, which docs_edit needs.
class DocsReadTool(
    private val queries: DocumentQueryUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_read",
            title = "Read a document",
            description =
                "Reads a document as markdown. format markdown (default) puts <!-- b:ID h:HASH --> before every block; use those with " +
                    "docs_edit. " +
                    "plain has no annotations; outline lists headings and block ids with sizes. Large documents come in chunks: pass " +
                    "cursor to continue.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "format" to Schemas.choice("Output format", listOf("markdown", "plain", "outline")),
                    "section" to Schemas.string("Heading block id: read that section only"),
                    "blocks" to Schemas.strings("Block ids: read only these blocks"),
                    "cursor" to Schemas.integer("cursor named at the end of the previous chunk"),
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
            )
        val chunk = queries.markdown(context.access, DocumentId(arguments.uuid("document")), request)
        // The front matter carries the document id and revision; a cut-off page says where to continue.
        val more =
            chunk.nextCursor
                ?.let { "\n<!-- more blocks follow (${chunk.totalBlocks} in total): call docs_read again with cursor $it -->\n" }
                .orEmpty()
        return McpToolResult.markdown(chunk.text + more)
    }
}
