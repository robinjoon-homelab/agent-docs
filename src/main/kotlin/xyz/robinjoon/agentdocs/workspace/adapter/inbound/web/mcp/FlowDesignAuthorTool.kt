package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.DesignAuthorConflict
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.DesignRequest
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.FlowDesignAuthorActions
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext

class FlowDesignAuthorTool(
    private val actions: FlowDesignAuthorActions,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "flow_design_author",
            title = "Add a design screen, component or child layer",
            description =
                "Create an editable v4 block from one div, section or button root. " +
                    "Use p/span text, strong/em/del runs, flex/grid, svg with M/L/C/Z paths, :root color tokens, " +
                    "simple CSS classes, template[data-component] or flow-use[component]. Unsupported markup reports its position. " +
                    "Example: <section><p>Title</p></section>. Read baseVersion first and reuse opId on retry.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document UUID or viewer URL"),
                    "baseVersion" to Schemas.integer("Content version observed when reading this document"),
                    "kind" to Schemas.choice("Design to create", listOf("screen", "component", "layer")),
                    "name" to Schemas.string("Required display name for screen or component"),
                    "parent" to Schemas.obj("For layer, append to this directly owned box address {owner,node}"),
                    "designId" to Schemas.string("Optional stable model identifier for referencing the design"),
                    "props" to Schemas.strings("Optional text property names for a component; bind with data-bind"),
                    "markup" to Schemas.string("Strict HTML/CSS and SVG subset, maximum 200000 characters"),
                    "opId" to Schemas.operationId,
                    "summary" to Schemas.string("Optional revision summary"),
                ),
            required = listOf("document", "kind", "markup", "baseVersion"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult =
        try {
            McpToolResult.data(actions.author(context, DesignRequest(arguments.node).document(), arguments.node))
        } catch (conflict: DesignAuthorConflict) {
            McpToolResult.error("conflict", conflict.message.orEmpty(), conflict.fix, mapOf("currentVersion" to conflict.version))
        }
}
