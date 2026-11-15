package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode

internal class MarkdownChunks(
    private val document: String,
) {
    private val pieces = arrayListOf<String>()
    private var version: Long? = null
    var cursor: Int = 0
        private set

    fun accept(chunk: JsonNode) {
        require(
            chunk.path("documentId").asString() == document && chunk.path("version").isIntegralNumber,
        ) { "invalid markdown chunk identity" }
        val current = chunk.path("version").asLong()
        if (version != null && version != current) throw CliFailure("version_changed", "Document changed during pull; run pull again")
        version = current
        pieces += chunk.path("markdown").asString()
        val next = chunk.path("nextCursor")
        val nextCursor = if (next.isNull || next.isMissingNode) null else next.asInt()
        require(nextCursor == null || nextCursor > cursor) { "non-advancing markdown cursor" }
        cursor = nextCursor ?: -1
    }

    fun text(): String {
        require(cursor == -1) { "markdown download is incomplete" }
        return pieces.joinToString("\n\n").trimEnd() + "\n"
    }
}
