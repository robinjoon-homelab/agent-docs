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
) : IllegalStateException(code, cause)
