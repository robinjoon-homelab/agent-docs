package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

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
    fun toJson(): Map<String, Any> =
        mapOf(
            "name" to name,
            "title" to title,
            "description" to description,
            "inputSchema" to mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false),
            "annotations" to
                mapOf(
                    "title" to title,
                    "readOnlyHint" to readOnly,
                    "destructiveHint" to false,
                    "idempotentHint" to readOnly,
                    "openWorldHint" to false,
                ),
        )
}
