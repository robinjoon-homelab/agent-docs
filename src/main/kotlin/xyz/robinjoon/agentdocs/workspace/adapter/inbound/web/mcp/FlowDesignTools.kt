package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.DesignRequest
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.FlowDesignActions
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext

class FlowDesignReadTool(
    private val actions: FlowDesignActions,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "flow_design_read",
            title = "Find design screens, states and node occurrences",
            description =
                "Pass a selected URL unchanged as document, with no other fields. For a numbered board search just the number: " +
                    "a board may be a screen or state. Use the returned block IDs with view=nodes; query scrollable finds scroll owners. " +
                    "Search reusable components and tokens with view=library. " +
                    "For a vector, pass its target and view=parts to read stable vertex IDs, guarded coordinates and the viewer link.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document UUID or viewer URL"),
                    "view" to Schemas.choice("Scope, defaults to blocks", listOf("blocks", "nodes", "parts", "library")),
                    "owner" to Schemas.string("For library: a component model ID to list its properties and variants"),
                    "target" to Schemas.obj("For parts: the vector occurrence copied from a nodes result"),
                    "query" to Schemas.string("Substring of name, identifier or description; for nodes, identifier, name, label or text"),
                    "kind" to Schemas.string("Optional block or node kind, for example screen, state, component, instance or box"),
                    "screen" to Schemas.string("Screen server block ID; required for nodes and parts"),
                    "state" to Schemas.string("State server block ID; pass with screen to get patch guards"),
                    "cursor" to Schemas.string("nextCursor from the same query"),
                    "limit" to Schemas.integer("Items per page, 1..30; default 12"),
                ),
            required = listOf("document"),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult = McpToolResult.data(actions.read(context.access, DesignRequest(arguments.node).document(), arguments.node))
}

class FlowDesignPatchTool(
    private val actions: FlowDesignActions,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "flow_design_patch",
            title = "Change one design state field",
            description =
                "Use a node result URL as document, or supply screen, state and target. Copy guards from the node result. " +
                    "For one vector vertex, copy parts/target/guards from view=parts and set path=x or y. Returns a hash and viewer link.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document UUID or viewer URL"),
                    "screen" to Schemas.string("Screen server block ID"),
                    "state" to Schemas.string("State server block ID"),
                    "target" to Schemas.obj("Exact owner, node and optional via from read"),
                    "guards" to Schemas.obj("Map of server block IDs to hashes from read"),
                    "path" to Schemas.choice("Property to set", listOf("scroll.x", "scroll.y", "visible", "text", "x", "y")),
                    "parts" to Schemas.objects("For x or y, copy the contour/vertex ID path or network vertex ID path from read"),
                    "value" to
                        mapOf(
                            "description" to
                                "Scroll: nonnegative px or end. visible: boolean. text: string. Vertex x/y: finite local coordinate.",
                            "type" to listOf("string", "number", "boolean"),
                        ),
                    "opId" to Schemas.operationId,
                    "summary" to Schemas.string("Optional revision summary"),
                ),
            required = listOf("document", "guards", "path", "value"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult = McpToolResult.data(actions.patch(context, DesignRequest(arguments.node).document(), arguments.node))
}
