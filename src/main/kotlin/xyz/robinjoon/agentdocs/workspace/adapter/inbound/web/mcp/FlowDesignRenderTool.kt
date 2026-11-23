package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.DesignRequest
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.renderSelection
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.FlowDesignRenderUseCase

class FlowDesignRenderTool(
    private val renders: FlowDesignRenderUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "flow_design_render",
            title = "Measure a rendered screen or selection",
            description =
                "Render the selected v4 screen/state in an isolated offline Chromium and read layout diagnostics. " +
                    "Use target from flow_design_read for a node and image=true for a cropped PNG.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document UUID or viewer URL"),
                    "screen" to Schemas.string("Screen server block ID; omit if present in the viewer link"),
                    "state" to Schemas.string("Optional state server block ID"),
                    "target" to Schemas.obj("Optional exact owner, node and via from the reader"),
                    "mode" to Schemas.choice("Capture extent", listOf("viewport", "full")),
                    "image" to Schemas.boolean("Include the selected PNG; defaults to false"),
                ),
            required = listOf("document"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val result = renders.render(context.access, DesignRequest(arguments.node).document(), renderSelection(arguments.node))
        return McpToolResult.render(result.measurements, result.png)
    }
}
