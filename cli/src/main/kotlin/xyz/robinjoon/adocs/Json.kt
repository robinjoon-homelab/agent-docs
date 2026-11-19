package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal object Json {
    val mapper = JsonMapper.builder().build()

    fun read(text: String): JsonNode = mapper.readTree(text)

    fun write(value: Any?): String = mapper.writeValueAsString(value)
}

internal class CliFailure(
    val code: String,
    val fix: String,
    val status: Int = 0,
    val retryAfter: String? = null,
    cause: Throwable? = null,
    val evidence: Map<String, JsonNode> = emptyMap(),
) : IllegalStateException(code, cause) {
    fun output(json: Boolean): String {
        val fields = mapOf("code" to code, "fix" to fix, "status" to status, "retryAfter" to retryAfter) + evidence
        if (json) return Json.write(fields)
        val message = "$code: $fix" + (retryAfter?.let { " (Retry-After: $it)" } ?: "")
        return if (evidence.isEmpty()) {
            message
        } else {
            message + "\n" +
                Json.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence)
        }
    }

    companion object {
        private val currentStateFields = setOf("conflicts", "currentVersion", "currentDocument")

        fun fromServer(
            error: JsonNode,
            status: Int,
            retryAfter: String? = null,
        ): CliFailure {
            val evidence = error.properties().filter { it.key in currentStateFields }.associate { it.key to it.value }
            return CliFailure(
                error.path("code").asString(error.path("error").asString("http_$status")),
                error.path("fix").asString("Inspect the request and granted permissions"),
                status,
                retryAfter,
                evidence = evidence,
            )
        }
    }
}
