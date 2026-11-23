package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ReadOptionsTest {
    private val read = CommandRegistry.routes.getValue("read")
    private val id = "00000000-0000-4000-8000-000000000001"

    private fun query(vararg args: String): Map<String, String> {
        val uri = URI(CommandRegistry.path(read, Arguments(listOf("read", id) + args), { "unused" }))
        assertEquals("/api/documents/$id/markdown", uri.path)
        return uri.rawQuery
            .split("&")
            .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8) }
    }

    @Test
    fun `flow options become encoded flow query parameters`() {
        val parameters =
            query("--flow-text", "저장 & 취소", "--flow-kind", "button", "--flow-screen", "M-01", "--flow-limit", "5", "--flow-offset", "10")

        assertEquals(
            mapOf("flowText" to "저장 & 취소", "flowKind" to "button", "flowScreen" to "M-01", "flowLimit" to "5", "flowOffset" to "10"),
            parameters,
        )
    }

    @Test
    fun `flow options join a raw query and a plain read keeps its path`() {
        assertEquals(mapOf("revision" to "3", "flowKind" to "screen"), query("--query", "revision=3", "--flow-kind", "screen"))
        assertEquals("/api/documents/$id/markdown", CommandRegistry.path(read, Arguments(listOf("read", id)), { "unused" }))
    }
}
