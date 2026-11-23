package xyz.robinjoon.agentdocs.workspace.domain.thread

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock

class DesignCommentTargetTest {
    private val screen =
        block(
            "screen01",
            """{"kind":"screen","id":"home","root":{"id":"root","kind":"box","children":[
        {"id":"first","kind":"box","style":{"background":{"token":"brand"}}},{"id":"other","kind":"text","text":"old"}]}}""",
        )
    private val colors = block("tokens01", """{"kind":"tokens","id":"palette","entries":[{"id":"brand","type":"color","value":"#fff"}]}""")
    private val annotation =
        block("comment1", """{"kind":"annotation","id":"a","screen":"home","target":{"owner":"home","node":"first"}}""")
    private val state =
        block(
            "state001",
            """{"kind":"state","id":"more","screen":"home","changes":[{"op":"set","target":{
        "owner":"home","node":"first"},"fields":{"visible":false}}]}""",
        )
    private val original = listOf(screen, colors, annotation, state)

    private fun block(
        id: String,
        json: String,
    ) = FlowDesignBlock.parse(BlockId(id), json)

    private fun target(
        block: FlowDesignBlock,
        current: List<Block>,
    ): String = FlowAnchorTarget.of(Anchor.Whole(block.id, ""), original, current)

    @Test
    fun `an annotation only tracks its addressed drawing but follows tokens across blocks`() {
        val other = FlowDesignBlock.parse(screen.id, screen.json.replace("old", "new"))
        assertThat(target(annotation, original - screen + other)).isEqualTo("ok")
        val token = FlowDesignBlock.parse(colors.id, colors.json.replace("#fff", "#000"))
        assertThat(target(annotation, original - colors + token)).isEqualTo("changed")
        assertThat(target(state, original - colors + token)).isEqualTo("changed")
        assertThat(target(screen, original - colors + token)).isEqualTo("changed")
    }

    @Test
    fun `missing drawing screen and state are distinguished by their retained ids`() {
        val changed = FlowDesignBlock.parse(screen.id, screen.json.replace("first", "new"))
        assertThat(target(annotation, original - screen + changed)).isEqualTo("elementDeleted")
        assertThat(target(annotation, original - screen)).isEqualTo("screenDeleted")
        assertThat(target(state, original - state)).isEqualTo("elementDeleted")
        assertThat(target(screen, original - state)).isEqualTo("changed")
    }

    @Test
    fun `a default annotation can remember the viewed state and never follows a replacement state`() {
        val anchor = Anchor.Whole(annotation.id, "", state.id)
        val updated = FlowDesignBlock.parse(state.id, state.json.replace("false", "true"))
        assertThat(FlowAnchorTarget.of(anchor, original, original - state + updated)).isEqualTo("changed")
        assertThat(target(annotation, original - state + updated)).isEqualTo("ok")
        assertThat(FlowAnchorTarget.of(anchor, original, original - state)).isEqualTo("elementDeleted")
        assertThat(AnchorResolver.resolve(anchor, original - state)).isEqualTo(AnchorResolution.Detached)
        val recreated = state.withId(BlockId("state002"))
        assertThat(FlowAnchorTarget.of(anchor, original, original - state + recreated)).isEqualTo("elementDeleted")
    }
}
