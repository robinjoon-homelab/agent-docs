package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

class StatsEditStartTool(
    private val sessions: EditSessionUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            "stats_edit_start",
            "Start edit time",
            "Records edit time only; never locks or changes a document.",
            mapOf("document" to Schemas.string("Document ID"), "opId" to Schemas.string("Idempotency key")),
            listOf("document", "opId"),
            false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult = McpToolResult.data(EditStatisticsJson.session(sessions.start(context, DocumentId(arguments.uuid("document")))))
}

class StatsEditEndTool(
    private val sessions: EditSessionUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            "stats_edit_end",
            "End edit time",
            "Closes an edit-time session; already closed results remain unchanged.",
            mapOf("session" to Schemas.string("Edit session ID"), "opId" to Schemas.string("Idempotency key")),
            listOf("session", "opId"),
            false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult = McpToolResult.data(EditStatisticsJson.session(sessions.end(context, arguments.uuid("session"))))
}
