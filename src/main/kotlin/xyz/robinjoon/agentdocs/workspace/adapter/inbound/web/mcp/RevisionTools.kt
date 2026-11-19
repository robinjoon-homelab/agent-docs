package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.RevisionMetadataJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.Wire
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.countConflict
import xyz.robinjoon.agentdocs.workspace.application.model.BlockRestore
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

class RevisionNameTool(
    private val documents: DocumentUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_revision_name",
            title = "Name a revision",
            description = "Names a confirmed revision without changing its content or version. Empty name removes the label.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "revision" to Schemas.integer("Confirmed revision"),
                    "name" to Schemas.string("At most 100 characters, empty to remove"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("document", "revision", "name"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val label =
            documents.nameRevision(
                context,
                DocumentId(arguments.uuid("document")),
                arguments.optionalInt("revision")
                    ?: throw InvalidRequestException("revision is required"),
                Wire.required(arguments.node, "name"),
            )
        return McpToolResult.data(mapOf("documentId" to label.documentId.value, "revision" to label.revision, "name" to label.name))
    }
}

class RestoreBlockTool(
    private val documents: DocumentUseCase,
    private val recorder: EditStatisticsRecorder,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_restore_block",
            title = "Restore a block",
            description =
                "Restores one historical block with its original block and table IDs. " +
                    "Other content and comments stay unchanged. Needs current baseVersion.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "revision" to Schemas.integer("Source revision"),
                    "blockId" to Schemas.string("Original block id"),
                    "baseVersion" to Schemas.integer("Current document version"),
                    "codeReferences" to Schemas.objects("Related repository, commit SHA or pullRequest"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("document", "revision", "blockId", "baseVersion"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val request =
            BlockRestore(requiredInt(arguments, "revision"), BlockId(arguments.string("blockId")), requiredInt(arguments, "baseVersion"))
        val document = DocumentId(arguments.uuid("document"))
        val written =
            recorder.countConflict(context.access, document) {
                documents.restoreBlock(RevisionMetadataJson.context(context, arguments.node), document, request)
            }
        recorder.agent(context.access, written.document.id)
        return WriteResults.of(written, false)
    }

    private fun requiredInt(
        arguments: Arguments,
        field: String,
    ): Int =
        arguments.optionalInt(field)
            ?: throw InvalidRequestException("$field is required")
}
