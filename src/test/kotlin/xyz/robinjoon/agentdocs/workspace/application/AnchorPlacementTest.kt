package xyz.robinjoon.agentdocs.workspace.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.service.AnchorPlacement
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DrawingBlock
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import java.util.UUID

class AnchorPlacementTest {
    private val drawing =
        DrawingBlock(
            BlockId("draw000001"),
            AssetId(UUID.fromString("abcdef01-2345-4bcd-8def-abcdef012345")),
            AssetId(UUID.fromString("fedcba98-7654-4321-8fed-cba987654321")),
            "배포 흐름 ".repeat(50),
        )
    private val document = WorkspaceFixtures.document(listOf(drawing), 1)

    @Test
    fun `a comment on a drawing anchors to the whole block and quotes the start of its alt text`() {
        val anchor = AnchorPlacement.place(AnchorRequest.Whole(drawing.id), document)

        assertThat(anchor).isEqualTo(Anchor.Whole(drawing.id, drawing.alt.take(200)))
    }

    @Test
    fun `a drawing has no text range to anchor to, even when the quote is in its alt text`() {
        assertThatThrownBy { AnchorPlacement.place(AnchorRequest.Text(drawing.id, "배포", 0), document) }
            .isInstanceOf(InvalidRequestException::class.java)
    }
}
