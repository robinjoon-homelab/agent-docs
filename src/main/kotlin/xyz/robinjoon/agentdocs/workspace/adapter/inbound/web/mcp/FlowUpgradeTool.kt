package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.RevisionMetadataJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.countConflict
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

class FlowUpgradeTool(
    private val documents: DocumentUseCase,
    private val recorder: EditStatisticsRecorder,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "flow_design_upgrade",
            title = "Upgrade a wireframe document",
            description = "Convert a legacy flow document into editable components, preserving links and history. Requires edit access.",
            properties =
                mapOf(
                    "document" to Schemas.string("Flow document ID"),
                    "baseVersion" to Schemas.integer("The content version you read"),
                    "opId" to Schemas.operationId,
                    "summary" to Schemas.string("One-line change summary"),
                ),
            required = listOf("document", "baseVersion"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val id = DocumentId(arguments.uuid("document"))
        val version = arguments.optionalInt("baseVersion") ?: throw InvalidRequestException("baseVersion is required")
        val result =
            recorder.countConflict(context.access, id) {
                documents.upgradeFlow(RevisionMetadataJson.context(context, arguments.node), id, version)
            }
        recorder.agent(context.access, id)
        return WriteResults.of(result, all = false)
    }
}
