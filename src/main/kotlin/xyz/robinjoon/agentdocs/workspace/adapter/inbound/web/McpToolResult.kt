package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

// What a tool returns. Tools declare no output schema, so a result reaches the model as text: markdown for documents
// and diffs, and the data serialized as JSON for everything else, errors included. notes follow the main text as
// separate text blocks, so a hint such as the space's agent guide never changes the bytes of the document markdown.
class McpToolResult private constructor(
    val markdown: String?,
    val data: Map<String, Any?>?,
    val isError: Boolean,
    val notes: List<String>,
) {
    companion object {
        fun data(data: Map<String, Any?>): McpToolResult = McpToolResult(null, data, false, emptyList())

        fun markdown(
            text: String,
            notes: List<String> = emptyList(),
        ): McpToolResult = McpToolResult(text, null, false, notes.toList())

        // code is the same error code the REST API uses; fix tells the agent what to do next.
        fun error(
            code: String,
            message: String,
            fix: String?,
            details: Map<String, Any?> = emptyMap(),
        ): McpToolResult = McpToolResult(null, mapOf("code" to code, "message" to message, "fix" to fix) + details, true, emptyList())
    }
}
