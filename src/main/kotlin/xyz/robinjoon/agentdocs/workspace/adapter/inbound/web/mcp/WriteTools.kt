package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.BlockJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.application.model.AssigneeChange
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

class DocsCreateTool(
    private val documents: DocumentUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_create",
            title = "Create a document",
            description =
                "Creates a document from markdown. Images must already be uploaded with docs_upload and referenced as ![alt](asset:ID).",
            properties =
                mapOf(
                    "space" to Schemas.string("Space slug"),
                    "path" to Schemas.string("Path inside the space such as payments/retry-design"),
                    "title" to Schemas.string("Title"),
                    "markdown" to Schemas.string("Body as GFM markdown, without front matter"),
                    "status" to Schemas.choice("Initial status", listOf("draft", "in_review", "confirmed", "archived")),
                    "tags" to Schemas.strings("Tags"),
                    "assignee" to Schemas.string("GitHub login of the assignee"),
                    "summary" to Schemas.string("One-line change summary for history"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("space", "path", "title", "markdown"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val properties =
            DocumentProperties.of(
                arguments.strings("tags").orEmpty(),
                statusOf(arguments.optionalString("status")) ?: DocumentStatus.DRAFT,
                arguments.optionalString("assignee")?.removePrefix("@")?.ifBlank { null },
            )
        val blocks = MarkdownReader.readBlocks(arguments.string("markdown")).map { it.block }
        val draft = DocumentDraft(DocumentPath(arguments.string("path")), arguments.string("title"), properties, blocks)
        return WriteResults.of(documents.create(context, SpaceSlug(arguments.string("space")), draft), all = true)
    }
}

// The way agents change documents: small guarded operations, applied together or not at all.
class DocsEditTool(
    private val documents: DocumentUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_edit",
            title = "Edit a document",
            description =
                "Applies operations atomically. Each op: {op: replace|insert|delete|move|replaceText, ...}. replace/delete/move need " +
                    "blockId and " +
                    "ifHash from docs_read; replace and insert take markdown; insert and move take after or before a block id, or at " +
                    "start|end; " +
                    "replaceText takes blockId, find (must occur once), replace. If a block changed since you read it, nothing is " +
                    "applied and the " +
                    "error returns the current block: read it and retry. Pass answering with a thread id to link the new revision to " +
                    "that thread.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "operations" to Schemas.objects("Operations, applied in order"),
                    "answering" to Schemas.string("Thread id this edit answers"),
                    "summary" to Schemas.string("One-line change summary for history"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("document", "operations"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val answering = arguments.optionalUuid("answering")?.let(::ThreadId)
        val written =
            documents.edit(
                context,
                DocumentId(arguments.uuid("document")),
                EditJson.operations(arguments.node.get("operations")),
                answering,
            )
        return WriteResults.of(written, all = false)
    }
}

class DocsMoveTool(
    private val documents: DocumentUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_move",
            title = "Move, rename, or relabel a document",
            description =
                "Changes a document's path inside its space, its title, status, tags, or assignee. The document id stays the same, so " +
                    "links, history, and comments follow.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "path" to Schemas.string("New path in the same space"),
                    "title" to Schemas.string("New title"),
                    "status" to Schemas.choice("New status", listOf("draft", "in_review", "confirmed", "archived")),
                    "tags" to Schemas.strings("Replace the tags"),
                    "assignee" to Schemas.string("GitHub login of the assignee; empty string clears"),
                    "baseRevision" to Schemas.integer("The revision you last read; a field a person changed since is a conflict"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("document", "baseRevision"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val assignee = if (arguments.node.has("assignee")) AssigneeChange(arguments.optionalString("assignee")) else null
        val change =
            HeaderChange(
                arguments.optionalString("title"),
                arguments.optionalString("path")?.let(::DocumentPath),
                statusOf(arguments.optionalString("status")),
                arguments.strings("tags"),
                assignee,
                arguments.optionalInt("baseRevision") ?: throw InvalidRequestException("docs_move needs baseRevision", READ_FIRST),
            )
        return WriteResults.of(documents.changeHeader(context, DocumentId(arguments.uuid("document")), change), all = false)
    }
}

internal const val READ_FIRST = "read the document with docs_read and pass the revision it shows"

internal fun statusOf(value: String?): DocumentStatus? =
    value?.let { name ->
        DocumentStatus.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw InvalidRequestException("status must be draft, in_review, confirmed, or archived")
    }

// What a write tool answers: the new revision and the hashes the agent needs for its next edit.
internal object WriteResults {
    fun of(
        written: DocumentWrite,
        all: Boolean,
    ): McpToolResult {
        val changed = written.changedBlocks.toSet()
        val blocks =
            written.document.blocks.filter { all || it.id in changed }.map {
                mapOf(
                    "id" to it.id.value,
                    "hash" to BlockJson.write(it)["hash"],
                )
            }
        return McpToolResult.data(
            mapOf(
                "documentId" to written.document.id.value,
                "revision" to written.revision,
                "unchanged" to written.unchanged,
                "changedBlockIds" to written.changedBlocks.map { it.value },
                "blocks" to blocks,
            ),
        )
    }
}
