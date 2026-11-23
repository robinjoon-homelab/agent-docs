package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockRestoration
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.FlowAnchorTarget

class LegacyFlowUpgradeTest {
    private val settings = FlowSettings(BlockId("settings"), "phone", "M", 19)
    private val screen = FlowScreen(BlockId("screen01"), "M-01", "메모", "홈에서", FlowFrame("custom", 500, 800), "설명")
    private val note = FlowScreen(BlockId("note0001"), "M-02", "도움말", frame = FlowFrame("note"), desc = "**내용**")
    private val emptyBehavior = FlowBehavior.of(FlowControls(false, null, true), emptyList(), emptyList())

    private fun element(
        id: String,
        kind: String,
        value: String = "",
    ) = FlowElement(
        BlockId(id),
        FlowAppearance.of(kind, FlowAppearance.defaultLook(kind), if (kind == "list") emptyList() else null, null),
        FlowPlacement(9, 31, 150, 40),
        FlowDescription("이름", value, "설명"),
        emptyBehavior,
    )

    private fun map(value: Any?): Map<*, *> = requireNotNull(value as? Map<*, *>)

    private fun maps(value: Any?): List<Map<*, *>> = requireNotNull(value as? List<*>).map(::map)

    private fun children(screen: FlowDesignBlock): List<Map<*, *>> = maps(map(screen.fields["root"])["children"])

    @Test
    fun `restoring an old element restores the drawing and its original comment target together`() {
        val button = element("button01", "button", "저장")
        val legacy = listOf(settings, screen, button, element("title001", "title"))
        val upgraded = LegacyFlowUpgrade.convert(legacy)
        val anchor = Anchor.Whole(button.id, "저장")
        assertThat(FlowAnchorTarget.of(anchor, legacy, upgraded)).isEqualTo("ok")
        val home = upgraded.single { it.id == screen.id }
        val currentRoot = map(home.fields["root"]) + ("children" to children(home).drop(1))
        val edited = home.withFields(home.fields + ("root" to currentRoot) + ("description" to "사람이 수정"))
        val deleted = upgraded.filter { it.id != button.id }.map { if (it.id == home.id) edited else it }
        val restored = BlockRestoration.restore(deleted, legacy, button.id).filterIsInstance<FlowDesignBlock>()
        val restoredScreen = restored.single { it.id == screen.id }
        assertThat(children(restoredScreen)).isEqualTo(children(home))
        assertThat(restoredScreen.string("description")).isEqualTo("사람이 수정")
        assertThat(FlowAnchorTarget.of(anchor, legacy, restored)).isEqualTo("ok")
        assertThatThrownBy { BlockRestoration.restore(deleted - edited, legacy, button.id) }.hasMessageContaining("screen first")
    }

    @Test
    fun `upgrade keeps screen and element identities order and navigation`() {
        val button =
            element("button01", "button", "저장").copy(
                behavior =
                    FlowBehavior.of(
                        FlowControls(true, "grp", false),
                        listOf(FlowAction("완료", "M-02", "sheet"), FlowAction("옛 링크", "M-20")),
                        listOf("열림", "닫힘"),
                    ),
            )
        val source = listOf(settings, screen, button, element("title001", "title"), note)
        val blocks = LegacyFlowUpgrade.convert(source)
        val first = blocks.single { it.id == screen.id }
        val annotation = blocks.single { it.id == button.id }
        val nodes = children(first)
        assertThat(blocks.first().fields["nextScreen"].toString()).isEqualTo("19")
        assertThat(blocks.filter { it.kind == "component" }).hasSize(16)
        assertThat(first.fields["entry"]).isEqualTo("홈에서")
        assertThat(map(first.fields["frame"])["height"]).isEqualTo(800.toBigDecimal().stripTrailingZeros())
        assertThat(nodes.map { it["id"] }).containsExactly("button01", "title001")
        assertThat(nodes[0]["group"]).isEqualTo("grp")
        assertThat(nodes[0]["locked"]).isEqualTo(true)
        assertThat(map(map(nodes[0]["position"])["x"])["start"].toString()).isEqualTo("9")
        assertThat(map(map(nodes[0]["size"])["y"])["px"]).isEqualTo(40.toBigDecimal().stripTrailingZeros())
        assertThat(map(nodes[0]["inputs"])["value"]).isEqualTo("저장")
        assertThat(annotation.fields["target"]).isEqualTo(mapOf("owner" to "screen01", "node" to "button01"))
        assertThat(annotation.fields["enabled"]).isEqualTo(false)
        assertThat(annotation.fields["legacyStateNames"]).isEqualTo(listOf("열림", "닫힘"))
        val actions = maps(annotation.fields["actions"])
        assertThat(actions[0]["to"]).isEqualTo(mapOf("screen" to "note0001"))
        assertThat(actions[0]["mode"]).isEqualTo("sheet")
        assertThat(actions[1]["to"]).isEqualTo(mapOf("screen" to mapOf("unresolvedName" to "M-20")))
        assertThat(blocks.last().fields["frame"]).isEqualTo("note")
        assertThat(blocks.last().fields).doesNotContainKey("root")
        assertThat(blocks.last().fields["description"]).isEqualTo("**내용**")
        assertThat(LegacyFlowUpgrade.convert(source)).isEqualTo(blocks)
        assertThat(LegacyFlowUpgrade.convert(blocks)).isEqualTo(blocks)
    }

    @Test
    fun `blank dates list keys and image attachments keep their exact source distinctions`() {
        val path = element("path0001", "path")
        val emptyDate = path.copy(id = BlockId("path0002"), appearance = FlowAppearance.of("path", null, null, ""))
        val list = element("list0001", "list").copy(appearance = FlowAppearance.of("list", null, listOf("같음", "같음"), null))
        val asset = "54d2a75c-b37e-4fae-9c60-84e71ef130ce"
        val picture = element("image001", "image", "asset:$asset")
        val fallback = element("image002", "image", "asset:bad")
        val blocks = LegacyFlowUpgrade.convert(listOf(settings, screen, path, emptyDate, list, picture, fallback))
        val nodes = children(blocks.single { it.id == screen.id })
        assertThat("date" in map(nodes[0]["inputs"])).isFalse()
        assertThat(map(nodes[1]["inputs"])["date"]).isEqualTo("")
        val rows = maps(map(nodes[2]["inputs"])["items"])
        assertThat(rows.map { it["itemKey"] }).containsExactly("row-0", "row-1")
        assertThat(rows.map { map(it["fields"])["text"] }).containsExactly("같음", "같음")
        val images = maps(map(nodes[3]["slots"])["picture"])
        assertThat(images.single()["asset"]).isEqualTo(asset)
        assertThat("slots" in nodes[4]).isFalse()
        assertThat(map(nodes[4]["inputs"])["value"]).isEqualTo("asset:bad")
    }

    @Test
    fun `component outer IDs avoid collisions with preexisting user IDs`() {
        val firstConversion = LegacyFlowUpgrade.convert(listOf(settings, screen))
        val generated = firstConversion.first { it.kind == "component" }
        val collision = element(generated.id.value, "text")
        val again = LegacyFlowUpgrade.convert(listOf(settings, screen, collision))
        assertThat(again.single { it.id == collision.id }.kind).isEqualTo("annotation")
        assertThat(again.map { it.id }).doesNotHaveDuplicates()
        assertThatThrownBy { LegacyFlowUpgrade.convert(listOf(Paragraph(BlockId("abcd"), InlineText("plain")))) }
            .isInstanceOf(InvalidDocumentException::class.java)
    }
}
