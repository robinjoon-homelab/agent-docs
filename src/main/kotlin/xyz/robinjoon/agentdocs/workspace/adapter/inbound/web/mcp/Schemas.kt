package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

// JSON Schema fragments for tool inputs.
internal object Schemas {
    fun string(description: String): Map<String, Any> = mapOf("type" to "string", "description" to description)

    fun integer(description: String): Map<String, Any> = mapOf("type" to "integer", "description" to description)

    fun boolean(description: String): Map<String, Any> = mapOf("type" to "boolean", "description" to description)

    fun choice(
        description: String,
        values: List<String>,
    ): Map<String, Any> = mapOf("type" to "string", "enum" to values, "description" to description)

    fun strings(description: String): Map<String, Any> =
        mapOf(
            "type" to "array",
            "items" to mapOf("type" to "string"),
            "description" to description,
        )

    fun objects(description: String): Map<String, Any> =
        mapOf(
            "type" to "array",
            "items" to mapOf("type" to "object"),
            "description" to description,
        )

    fun obj(description: String): Map<String, Any> = mapOf("type" to "object", "description" to description)

    val operationId = string("Optional idempotency key. Retrying with the same opId applies the write once and returns the first result.")
}
