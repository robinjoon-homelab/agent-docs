package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

// What a tool returns: text for the model to read and, for JSON results, the same data as structured content.
class McpToolResult private constructor(
    val text: String?,
    val structured: Map<String, Any?>?,
    val isError: Boolean,
) {
    companion object {
        fun data(structured: Map<String, Any?>): McpToolResult = McpToolResult(null, structured, false)

        fun markdown(
            text: String,
            structured: Map<String, Any?>,
        ): McpToolResult = McpToolResult(text, structured, false)

        // code is the same error code the REST API uses; fix tells the agent what to do next.
        fun error(
            code: String,
            message: String,
            fix: String?,
            details: Map<String, Any?> = emptyMap(),
        ): McpToolResult = McpToolResult(null, mapOf("code" to code, "message" to message, "fix" to fix) + details, true)
    }
}
