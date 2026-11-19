package xyz.robinjoon.agentdocs.workspace.adapter.outbound.drawing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.model.DrawingText
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException

class JsonSceneVerifierTest {
    private val verifier = JsonSceneVerifier(JsonMapper.builder().build())

    private fun scene(
        elements: String,
        files: String = "{}",
    ): ByteArray = """{"type":"excalidraw","version":2,"source":"agent-docs","elements":[$elements],"files":$files}""".toByteArray()

    @Test
    fun `lists the visible text elements as typed and counts what a reader sees`() {
        val bytes =
            scene(
                """
                {"id":"box","type":"rectangle","isDeleted":false,"link":"https://example.com/plan"},
                {"id":"label","type":"text","text":"배포\n흐름","originalText":"배포 흐름","containerId":"box"},
                {"id":"note","type":"text","text":"메모","containerId":null},
                {"id":"gone","type":"text","text":"지운 글","isDeleted":true},
                {"id":"pic","type":"image","fileId":"f1"}
                """.trimIndent(),
                """{"f1":{"id":"f1","mimeType":"image/png","dataURL":"data:image/png;base64,iVBORw0KGgo="}}""",
            )

        val verified = verifier.verify(bytes)

        assertThat(verified.texts).containsExactly(DrawingText("label", "배포 흐름", "box"), DrawingText("note", "메모", null))
        assertThat(verified.elementCount).isEqualTo(4)
        assertThat(verified.fileCount).isEqualTo(1)
    }

    @Test
    fun `an AI-generated HTML frame is refused while a deleted one stays as history`() {
        val live = scene("""{"id":"ai","type":"iframe","customData":{"generationData":{"status":"done","html":"<img src=x>"}}}""")
        val deleted = scene("""{"id":"ai","type":"iframe","isDeleted":true}""")

        assertThatThrownBy { verifier.verify(live) }.isInstanceOf(InvalidAssetException::class.java)
        assertThat(verifier.verify(deleted).elementCount).isZero()
    }

    @Test
    fun `an image file given as an address is refused because opening the drawing would fetch it`() {
        val bytes =
            scene("""{"id":"pic","type":"image","fileId":"f1"}""", """{"f1":{"mimeType":"image/png","dataURL":"https://x.test/a.png"}}""")

        assertThatThrownBy { verifier.verify(bytes) }.isInstanceOf(InvalidAssetException::class.java)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"type":"excalidraw","elements":[{"id":"a","type":"rectangle"},{"id":"a","type":"ellipse"}]}""",
            """{"type":"excalidraw","elements":[{"id":"a","type":"hologram"}]}""",
            """{"type":"excalidraw","elements":[{"type":"rectangle"}]}""",
            """{"type":"excalidrawlib","elements":[]}""",
            """{"type":"excalidraw"}""",
            """{"type":"excalidraw","elements":[],"files":[]}""",
            """not json""",
        ],
    )
    fun `anything but a well-formed scene is refused`(json: String) {
        assertThatThrownBy { verifier.verify(json.toByteArray()) }.isInstanceOf(InvalidAssetException::class.java)
    }

    @Test
    fun `Liberation Sans is refused in visible elements and appState while deleted elements stay as history`() {
        val visibleFont = scene("""{"id":"t","type":"text","text":"hi","fontFamily":9}""")
        val defaultFont = """{"type":"excalidraw","version":2,"appState":{"currentItemFontFamily":9},"elements":[]}""".toByteArray()
        val deletedFont = scene("""{"id":"t","type":"text","text":"old","fontFamily":9,"isDeleted":true}""")
        val allowedFont = scene("""{"id":"t","type":"text","text":"ok","fontFamily":5}""")

        assertThatThrownBy { verifier.verify(visibleFont) }.isInstanceOf(InvalidAssetException::class.java)
        assertThatThrownBy { verifier.verify(defaultFont) }.isInstanceOf(InvalidAssetException::class.java)
        assertThat(verifier.verify(deletedFont).elementCount).isZero()
        assertThat(verifier.verify(allowedFont).elementCount).isEqualTo(1)
    }

    @Test
    fun `a scene that is not UTF-8 is refused because its source is read as UTF-8 text`() {
        val latin1 = """{"type":"excalidraw","elements":[{"id":"t","type":"text","text":"café"}]}""".toByteArray(Charsets.ISO_8859_1)

        assertThatThrownBy { verifier.verify(latin1) }.isInstanceOf(InvalidAssetException::class.java)
    }
}
