package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MarkdownChunksTest {
    @Test
    fun `all chunks must have the same version and advancing cursor`() {
        val chunks = MarkdownChunks("document")
        chunks.accept(chunk(10, 1))
        val failure = assertThrows(CliFailure::class.java) { chunks.accept(chunk(11, null)) }
        assertEquals("version_changed", failure.code)
        assertThrows(IllegalArgumentException::class.java) { chunks.text() }
    }

    @Test
    fun `complete chunks preserve annotated markdown`() {
        val chunks = MarkdownChunks("document")
        chunks.accept(chunk(10, 1))
        chunks.accept(chunk(10, null))
        assertEquals("<!-- b:aaaa h:hash -->\nText\n\n\n<!-- b:aaaa h:hash -->\nText\n", chunks.text())
    }

    @Test
    fun `foreign chunk or looping cursor is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { MarkdownChunks("foreign").accept(chunk(10, null)) }
        assertThrows(IllegalArgumentException::class.java) { MarkdownChunks("document").accept(chunk(10, 0)) }
    }

    private fun chunk(
        version: Int,
        next: Int?,
    ) = Json.read(
        Json.write(
            mapOf(
                "documentId" to "document",
                "version" to version,
                "nextCursor" to next,
                "markdown" to "<!-- b:aaaa h:hash -->\nText\n",
            ),
        ),
    )
}
