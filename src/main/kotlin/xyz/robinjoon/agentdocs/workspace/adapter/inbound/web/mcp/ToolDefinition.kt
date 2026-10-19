package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.modelcontextprotocol.spec.McpSchema

// A tool as tools/list describes it. Reads are marked read-only; writes are not destructive because every change is a
// revision that can be restored.
class ToolDefinition(
    val name: String,
    val title: String,
    val description: String,
    val properties: Map<String, Map<String, Any>>,
    val required: List<String>,
    val readOnly: Boolean,
) {
    fun toTool(): McpSchema.Tool =
        McpSchema.Tool
            .builder()
            .name(name)
            .title(title)
            .description(description)
            .inputSchema(
                mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false),
            ).annotations(McpSchema.ToolAnnotations(title, readOnly, false, readOnly, false, null))
            .build()
}
