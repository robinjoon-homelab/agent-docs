package xyz.robinjoon.agentdocs.workspace.adapter.outbound.image

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException

class SniffingImageVerifierTest {
    private val verifier = SniffingImageVerifier(SvgSanitizer())

    @Test
    fun `raster images are recognized by their signature whatever they claim to be`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1)
        val webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()

        assertThat(verifier.verify(png)?.mediaType).isEqualTo(AssetMediaType.PNG)
        assertThat(verifier.verify("GIF89a...".toByteArray())?.mediaType).isEqualTo(AssetMediaType.GIF)
        assertThat(verifier.verify(webp)?.mediaType).isEqualTo(AssetMediaType.WEBP)
        assertThat(verifier.verify("%PDF-1.7".toByteArray())).isNull()
    }

    @Test
    fun `an SVG loses scripts, event handlers, and outside links but keeps its drawing`() {
        val svg =
            """
            <?xml version="1.0"?>
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" onload="alert(1)">
              <script>alert(2)</script>
              <foreignObject><div>html</div></foreignObject>
              <a xlink:href="javascript:alert(3)"><rect width="10" height="10" onclick="x()"/></a>
              <use href="#shape"/>
              <circle r="5" style="fill: url(https://evil.example/x)"/>
            </svg>
            """.trimIndent()

        val cleaned = String(requireNotNull(verifier.verify(svg.toByteArray())).bytes)

        assertThat(cleaned).doesNotContain("script", "alert", "onload", "onclick", "foreignObject", "javascript:", "evil.example")
        assertThat(cleaned).contains("<rect", "href=\"#shape\"", "<circle")
    }

    @Test
    fun `an SVG with a DTD is refused so no entity can be expanded`() {
        val svg =
            "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" +
                "<svg xmlns=\"http://www.w3.org/2000/svg\">&x;</svg>"

        assertThatThrownBy { verifier.verify(svg.toByteArray()) }.isInstanceOf(InvalidAssetException::class.java)
    }
}
