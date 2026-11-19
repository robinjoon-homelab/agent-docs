package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class UploadPathTest {
    @Test
    fun `the upload operation key is an optional single encoded query value`() {
        val key = "기획 &v=3+1/#?"
        val uri = URI(CommandRegistry.uploadPath("my-space", key))

        assertEquals("/api/spaces/my-space/assets", uri.path)
        assertEquals(listOf("opId"), uri.rawQuery.split("&").map { it.substringBefore('=') })
        assertEquals(key, URLDecoder.decode(uri.rawQuery.substringAfter('='), StandardCharsets.UTF_8))
        assertEquals("/api/spaces/my-space/assets", CommandRegistry.uploadPath("my-space", null))
    }
}
