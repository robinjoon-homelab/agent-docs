package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class BlockJsonContentTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `display math survives JSON while old code stays fenced`() {
        val body = json.readTree("""{"id":"math000001","type":"code","language":"math","code":"x^2","displayMath":true}""")
        val math = BlockJson.read(body)

        assertThat(BlockJson.write(math)["displayMath"]).isEqualTo(true)
        assertThat(BlockMarkdown.of(math)).isEqualTo("$$\nx^2\n$$")
        val oldCode = CodeBlock(BlockId("math000001"), "math", "x^2")
        assertThat(BlockJson.write(oldCode)).doesNotContainKey("displayMath")
        assertThat(BlockMarkdown.of(oldCode)).isEqualTo("```math\nx^2\n```")
    }

    @Test
    fun `a drawing keeps both asset ids and its alt text through JSON and reads back from its markdown`() {
        val body =
            json.readTree(
                """{"id":"draw000001","type":"drawing","svgAssetId":"11111111-1111-4111-8111-111111111111",""" +
                    """"sceneAssetId":"22222222-2222-4222-8222-222222222222","alt":"흐름"}""",
            )
        val drawing = BlockJson.read(body)

        assertThat(BlockJson.write(drawing)).containsEntry("type", "drawing").containsEntry("alt", "흐름")
        assertThat(BlockJson.write(drawing)["svgAssetId"].toString()).isEqualTo("11111111-1111-4111-8111-111111111111")
        assertThat(BlockJson.write(drawing)["sceneAssetId"].toString()).isEqualTo("22222222-2222-4222-8222-222222222222")
        assertThat(BlockMarkdown.of(drawing)).isEqualTo(
            "![흐름](asset:11111111-1111-4111-8111-111111111111 \"excalidraw asset:22222222-2222-4222-8222-222222222222\")",
        )
    }

    @Test
    fun `an image whose caption marks a drawing is refused, so it cannot become a drawing that skipped the asset checks`() {
        val image = """{"id":"imag000001","type":"image","assetId":"11111111-1111-4111-8111-111111111111","""
        val reserved = json.readTree(image + """"caption":"excalidraw asset:abcdef01-2345-4bcd-8def-abcdef012345"}""")
        val near = json.readTree(image + """"caption":"Excalidraw asset:abcdef01-2345-4bcd-8def-abcdef012345"}""")

        assertThatThrownBy { BlockJson.read(reserved) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThat(BlockJson.read(near)).isInstanceOf(ImageBlock::class.java)
    }
}
