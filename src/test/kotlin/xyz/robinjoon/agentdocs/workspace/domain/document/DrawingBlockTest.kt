package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import java.util.UUID

class DrawingBlockTest {
    private val svg = AssetId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
    private val scene = AssetId(UUID.fromString("22222222-2222-4222-8222-222222222222"))
    private val drawing = DrawingBlock(BlockId("draw000001"), svg, scene, "배포 흐름 [초안]")

    @Test
    fun `a drawing reads as its SVG image with the scene named in the caption and reads back the same`() {
        val written = BlockMarkdown.write(listOf(drawing), annotated = true)

        val parsed = MarkdownReader.readBlocks(written)

        assertThat(BlockMarkdown.of(drawing)).isEqualTo("![배포 흐름 [초안\\]](asset:$svg \"excalidraw asset:$scene\")")
        assertThat(parsed.map { it.block }).containsExactly(drawing)
        assertThat(parsed.single().declaredHash).isEqualTo(BlockMarkdown.hash(drawing))
    }

    @Test
    fun `only the exact drawing caption names a scene, so existing images keep reading as images`() {
        val near = "Excalidraw asset:$scene"
        val loose = "excalidraw asset: $scene"
        val markdown = "![a](asset:$svg \"$near\")\n\n![b](asset:$svg \"$loose\")\n\n![c](asset:$svg \"mockup\")"
        val blocks = MarkdownReader.readBlocks(markdown)

        assertThat(blocks.map { it.block }).allSatisfy { assertThat(it).isInstanceOf(ImageBlock::class.java) }
        assertThat(blocks.map { (it.block as ImageBlock).caption }).containsExactly(near, loose, "mockup")
    }

    @Test
    fun `an agent may write the scene id in capitals and the stored form is canonical`() {
        val lettered = AssetId(UUID.fromString("abcdef01-2345-4bcd-8def-abcdef012345"))
        val upper = lettered.value.toString().uppercase()
        val parsed = MarkdownReader.readBlocks("![흐름](asset:$svg \"excalidraw asset:$upper\")").single().block

        assertThat(parsed).isEqualTo(DrawingBlock(parsed.id, svg, lettered, "흐름"))
        assertThat(BlockMarkdown.of(parsed)).contains("excalidraw asset:$lettered").doesNotContain(upper)
    }

    @Test
    fun `a drawing needs two different assets and a one-line alt text`() {
        assertThatThrownBy { DrawingBlock(BlockId("draw000002"), svg, svg, "") }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { DrawingBlock(BlockId("draw000003"), svg, scene, "두\n줄") }
            .isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy {
            DrawingBlock(BlockId("draw000004"), svg, scene, "가".repeat(ImageBlock.MAX_TEXT_LENGTH + 1))
        }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `both assets are references and each must have the type of its role`() {
        val other = AssetId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
        val blocks = listOf(drawing, ImageBlock(BlockId("imag000001"), other, "", ""))

        assertThat(AssetReferences.of(blocks)).containsExactlyInAnyOrder(svg, scene, other)
        assertThat(AssetReferences.expectedTypes(blocks)).isEqualTo(mapOf(svg to AssetMediaType.SVG, scene to AssetMediaType.EXCALIDRAW))
    }

    @Test
    fun `one asset cannot be one drawing's SVG and another drawing's scene`() {
        val crossed = DrawingBlock(BlockId("draw000005"), scene, svg, "")

        assertThatThrownBy { AssetReferences.expectedTypes(listOf(drawing, crossed)) }.isInstanceOf(InvalidDocumentException::class.java)
    }
}
