package xyz.robinjoon.agentdocs.workspace.application.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor

class DesignCommentPlacementTest {
    private fun block(
        id: String,
        body: String,
    ) = FlowDesignBlock.parse(BlockId(id), body)

    @Test
    fun `only annotations in the viewed state can acquire its context`() {
        val settings = block("settings", """{"kind":"settings","id":"settings","modelVersion":4}""")
        val screen = block("screen11", """{"kind":"screen","id":"first","code":"F-01"}""")
        val other = block("screen12", """{"kind":"screen","id":"other","code":"F-02"}""")
        val first = block("state001", """{"kind":"state","id":"on","screen":"first"}""")
        val foreign = block("state002", """{"kind":"state","id":"off","screen":"other"}""")
        val annotation = block("comment1", """{"kind":"annotation","id":"a","screen":"first","name":"버튼"}""")
        val doc =
            Document.create(
                WorkspaceFixtures.documentId,
                WorkspaceFixtures.header.copy(type = "flow"),
                listOf(settings, screen, other, first, foreign, annotation),
                WorkspaceFixtures.now,
            )
        val selected = AnchorPlacement.place(AnchorRequest.Whole(annotation.id, first.id), doc)
        assertThat(selected).isEqualTo(Anchor.Whole(annotation.id, "버튼", first.id))
        assertThatThrownBy { AnchorPlacement.place(AnchorRequest.Whole(annotation.id, foreign.id), doc) }
            .isInstanceOf(InvalidRequestException::class.java)
        assertThatThrownBy { AnchorPlacement.place(AnchorRequest.Whole(screen.id, first.id), doc) }
            .isInstanceOf(InvalidRequestException::class.java)
    }
}
