package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import java.util.UUID

class ImageBlockTest {
    private val image = AssetId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
    private val scene = AssetId(UUID.fromString("abcdef01-2345-4bcd-8def-abcdef012345"))

    @Test
    fun `an image cannot carry the caption that marks a drawing, whatever the letter case of the scene id`() {
        listOf("excalidraw asset:$scene", "excalidraw asset:${scene.value.toString().uppercase()}").forEach { caption ->
            assertThatThrownBy { ImageBlock(BlockId("imag000001"), image, "", caption) }
                .isInstanceOf(InvalidDocumentException::class.java)
        }
    }

    @Test
    fun `captions that only look like a drawing's stay ordinary images and read back as the same images`() {
        val captions =
            listOf(
                "Excalidraw asset:$scene",
                "excalidraw asset: $scene",
                "excalidraw asset:$scene ",
                "excalidraw asset:$image",
                "excalidraw asset:abcdef01-2345-4bcd-8def-abcdef01234g",
                "excalidraw asset:abcdef012-345-4bcd-8def-abcdef012345",
            )
        val images = captions.mapIndexed { index, caption -> ImageBlock(BlockId("imag00000$index"), image, "그림", caption) }

        val read = MarkdownReader.readBlocks(BlockMarkdown.write(images, annotated = true)).map { it.block }

        assertThat(read).containsExactlyElementsOf(images)
    }
}
