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

    @Test
    fun `a drawing keeps its inlined fonts, images, and fragment links while a stylesheet that loads anything is dropped`() {
        val svg =
            """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 10 10">
              <defs>
                <style class="style-fonts">@font-face { font-family: Excalifont; src: url(data:font/woff2;base64,d09GMg==); }</style>
                <style>@font-face { font-family: Leak; src: url(https://esm.sh/font.woff2); }</style>
                <style>@import url("https://evil.example/a.css");</style>
                <symbol id="image-f1"><image href="data:image/png;base64,iVBORw0KGgo=" width="100%" height="100%"/></symbol>
              </defs>
              <use href="#image-f1" mask="url(#mask-1)"/>
              <a href="https://example.com/plan"><rect width="5" height="5"/></a>
            </svg>
            """.trimIndent()

        val cleaned = String(requireNotNull(verifier.verify(svg.toByteArray())).bytes)

        assertThat(cleaned).contains("data:font/woff2;base64,d09GMg==", "data:image/png;base64", "href=\"#image-f1\"")
        assertThat(cleaned).contains("url(#mask-1)", "<rect")
        assertThat(cleaned).doesNotContain("esm.sh", "evil.example", "example.com/plan")
    }

    @Test
    fun `quoted or spaced data and fragment urls keep their stylesheet while any other target drops it`() {
        val kept =
            listOf(
                "<style>.k1 { src: url('data:font/woff2;base64,AA'); }</style>",
                "<style>.k2 { fill: url( #gradient ); }</style>",
                "<style>.k3 { fill: url(\"#gradient\"); }</style>",
                "<style>.k4 { src: URL( \"DATA:font/woff2;base64,AA\" ); }</style>",
            )
        val dropped =
            listOf(
                "<style>.d1 { src: url('https://cdn.test/a.woff2'); }</style>",
                "<style>.d2 { src: url( //cdn.test/b.woff2 ); }</style>",
                "<style>.d3 { fill: url(images/c.png); }</style>",
                "<style>.d4 { src: url(\"https://cdn.test/d.woff2</style>",
                "<style>.d5 { background: image-set(\"https://cdn.test/e.png\" 1x); }</style>",
            )
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + (kept + dropped).joinToString("") + "</svg>"

        val cleaned = String(requireNotNull(verifier.verify(svg.toByteArray())).bytes)

        assertThat(cleaned).contains(".k1", ".k2", ".k3", ".k4")
        assertThat(cleaned).doesNotContain(".d1", ".d2", ".d3", ".d4", ".d5", "cdn.test")
    }

    @Test
    fun `a stylesheet that spells a load with CSS escapes is dropped while the exported font stays`() {
        val exported = "<style>@font-face { font-family: Excalifont; src: url(data:font/woff2;base64,d09GMg==); }</style>"
        val escaped =
            listOf(
                """<style>.e1 { src: u\72l(https://cdn.test/f.woff2); }</style>""",
                """<style>@\69mport "https://cdn.test/x.css";</style>""",
                """<style>.e3 { background: image-s\65t("https://cdn.test/g.png" 1x); }</style>""",
                // An XML character reference becomes the backslash before the stylesheet is read.
                """<style>.e4 { src: u&#92;72l(https://cdn.test/h.woff2); }</style>""",
            )
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + exported + escaped.joinToString("") + "</svg>"

        val cleaned = String(requireNotNull(verifier.verify(svg.toByteArray())).bytes)

        assertThat(cleaned).contains("data:font/woff2;base64,d09GMg==")
        assertThat(cleaned).doesNotContain(".e1", "mport", ".e3", ".e4", "cdn.test")
    }
}
