package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignScene
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.FlowAnchorTarget

class DesignRowTargetTest {
    private fun block(
        id: String,
        json: String,
    ) = FlowDesignBlock.parse(BlockId(id), json)

    private val groups =
        block(
            "groups01",
            """{"kind":"component","id":"groups","root":{"kind":"repeat","id":"group","items":{"prop":"rows"},
        "template":[{"kind":"repeat","id":"each","items":{"repeat":"group","field":"items"},"template":[{"kind":"text","id":"label"}]}]}}""",
        )
    private val card =
        block(
            "holder01",
            """{"kind":"component","id":"card","root":{"kind":"instance","id":"grid","component":"groups",
        "inputs":{"rows":[{"itemKey":"group1","fields":{"items":[{"itemKey":"a","fields":{"name":"old"}},{"itemKey":"b","fields":{}}]}}]}}}""",
        )
    private val path = """{"owner":"card","instance":"grid","component":"groups"}"""
    private val mount = """{"owner":"home","instance":"card","component":"card"}"""
    private val inside = """"prop":"rows","within":[{"itemKey":"group1","field":"items"}]"""
    private val screen =
        block(
            "screen01",
            """{"kind":"screen","id":"home","root":{"kind":"instance","id":"card","component":"card",
        "overrides":[{"op":"itemRemove","target":{"owner":"card","node":"grid"},$inside,"itemKey":"a"}]}}""",
        )
    private val row = """{"owner":"groups","node":"label","via":[$mount,$path,
        {"owner":"groups","repeat":"group","itemKey":"group1"},{"owner":"groups","repeat":"each","itemKey":"b"}]}"""
    private val annotation = block("annot001", """{"kind":"annotation","id":"note","screen":"home","target":$row}""")
    private val active = block("state001", """{"kind":"state","id":"open","screen":"home","changes":[]}""")
    private val original = listOf(screen, card, groups, annotation, active)
    private val target = """{"owner":"card","node":"grid","via":[$mount]}"""

    @Test
    fun `removing a row in the viewed state detaches only that occurrence after mounted overrides`() {
        val base = DesignScene(original, screen.id, active.id).occurrences().filter { it.target.node == "label" }
        assertThat(base.map { it.target.via.last()["itemKey"] }.toList()).containsExactly("b")
        val op = """{"op":"itemRemove","target":$target,$inside,"itemKey":"b"}"""
        val state = FlowDesignBlock.parse(active.id, active.json.replace("[]", "[$op]"))
        val now = original - active + state
        assertThat(FlowAnchorTarget.of(Anchor.Whole(annotation.id, "", active.id), original, now)).isEqualTo("elementDeleted")
        assertThat(FlowAnchorTarget.of(Anchor.Whole(annotation.id, ""), original, now)).isEqualTo("ok")
    }

    @Test
    fun `a caller node assigned to a repeated slot remains distinct in every row`() {
        val list =
            block(
                "groups01",
                """{"kind":"component","id":"groups","root":{"kind":"repeat","id":"each","items":{"prop":"rows"},
            "template":[{"kind":"slot","id":"place","prop":"body"}]}}""",
            )
        val source =
            block(
                "screen01",
                """{"kind":"screen","id":"home","root":{"kind":"instance","id":"list","component":"groups",
            "inputs":{"rows":[{"itemKey":"a","fields":{}},{"itemKey":"b","fields":{}}]},"slots":{"body":[{"id":"title","kind":"text"}]}}}""",
            )
        val titles = DesignScene(listOf(list, source), source.id, null).occurrences().filter { it.target.node == "title" }.toList()
        assertThat(titles.map { it.target.owner }).containsOnly("home")
        assertThat(titles.map { it.target.via.single() }).containsExactly(
            mapOf("owner" to "groups", "repeat" to "each", "itemKey" to "a"),
            mapOf("owner" to "groups", "repeat" to "each", "itemKey" to "b"),
        )
    }

    @Test
    fun `insert set and move use row keys within the requested nested list`() {
        val ops = """{"op":"itemInsert","target":$target,$inside,"item":{"itemKey":"c","fields":{"name":"new"}},"at":{"after":"b"}},
            {"op":"itemSet","target":$target,$inside,"itemKey":"c","field":"name","value":"changed"},
            {"op":"itemMove","target":$target,$inside,"itemKey":"c","at":{"edge":"first"}}"""
        val state = FlowDesignBlock.parse(active.id, active.json.replace("[]", "[$ops]"))
        val rows = DesignScene(original - active + state, screen.id, state.id).occurrences().filter { it.target.node == "label" }.toList()
        assertThat(rows.map { it.target.via.last()["itemKey"] }).containsExactly("c", "b")
        assertThat(rows.first().valueOf(mapOf("repeat" to "each", "field" to "name"))).isEqualTo("changed")
    }
}
