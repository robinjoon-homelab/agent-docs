package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.DocumentTransferJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.countConflict
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentTransfer
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentTransferUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class DocsTransferTool(
    private val documents: DocumentTransferUseCase,
    private val statistics: EditStatisticsRecorder,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_transfer",
            title = "Transfer a document to a space",
            description =
                "Transfers the document and live descendants atomically, preserving IDs, history, comments and attachments. " +
                    "Requires edit in both spaces and current baseVersion. Any destination path collision refuses the whole transfer. " +
                    "Reuses the first result for opId only while all included documents remain accessible.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "space" to Schemas.string("Destination space slug"),
                    "path" to Schemas.string("Destination path for the root document"),
                    "baseVersion" to Schemas.integer("Current content version"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("document", "space", "path", "baseVersion"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val base = arguments.optionalInt("baseVersion") ?: throw InvalidRequestException("baseVersion is required", READ_FIRST)
        val request = DocumentTransfer(SpaceSlug(arguments.string("space")), DocumentPath(arguments.string("path")), base)
        val id = DocumentId(arguments.uuid("document"))
        val written = statistics.countConflict(context.access, id) { documents.transfer(context, id, request) }
        statistics.agent(context.access, id)
        return McpToolResult.data(DocumentTransferJson.result(written))
    }
}
