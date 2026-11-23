package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignScene
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignVertexPath
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignVertices
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DesignField
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DesignFieldPatch
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class DesignVertexPatchTest {
    private fun block(
        id: String,
        json: String,
    ) = FlowDesignBlock.parse(BlockId(id), json)

    private val screen =
        block(
            "screen1",
            """
        {"kind":"screen","id":"home","code":"S-01","name":"홈","root":{"id":"root","kind":"box","layout":{"mode":"row"},"children":[
          {"id":"title","kind":"text","text":"제목"},
          {"id":"left","kind":"instance","component":"icon","overrides":[{"op":"detailSet","target":{"owner":"icon","node":"edge"},
            "parts":[{"collection":"contours","id":"line"},{"collection":"vertices","id":"tip"}],"field":"x","value":-5}]},
          {"id":"right","kind":"instance","component":"icon"}]}}
    """,
        )
    private val icon =
        block(
            "icon01",
            """
        {"kind":"component","id":"icon","name":"아이콘","props":[],"root":{"kind":"box","id":"root","layout":{"mode":"free"},"children":[
        {"kind":"text","id":"label","text":"사람이 고친 이름"},
        {"kind":"vector","id":"edge","fillRule":"nonzero","contours":[{"id":"line","closed":false,"vertices":[{"id":"tip","x":1,"y":2},{"id":"heel","x":7,"y":8}]}]}]}}
    """,
        )
    private val state = block("state1", """{"kind":"state","id":"alt","name":"선택","screen":"home","changes":[]}""")
    private val annotation =
        block(
            "note01",
            """{"kind":"annotation","id":"note","name":"제목","screen":"home","target":{"owner":"home","node":"title"},"description":"사람 댓글의 원래 위치"}""",
        )
    private val original = listOf(screen, icon, state, annotation)
    private val point = DesignVertexPath("line", "tip")

    private fun target(instance: String) =
        DesignAddress(
            "icon",
            "edge",
            listOf(
                mapOf(
                    "owner" to "home",
                    "instance" to instance,
                    "component" to "icon",
                ),
            ),
        )

    private fun guards(blocks: List<Block>) = blocks.filter { it.id != annotation.id }.associate { it.id to BlockMarkdown.hash(it) }

    private fun patch(
        hashes: Map<BlockId, String>,
        point: DesignVertexPath,
    ) = DesignFieldPatch(screen.id, state.id, target("left"), hashes, DesignField("x", 19.toBigDecimal(), point))

    private fun points(
        blocks: List<Block>,
        instance: String,
    ): DesignVertices {
        val scene = DesignScene(blocks, screen.id, state.id)
        return DesignVertices(scene.resolve(target(instance)), scene)
    }

    @Test
    fun `a fresh read patches only the chosen contour vertex after a person edits state text`() {
        val body = mapOf("op" to "set", "target" to mapOf("owner" to "home", "node" to "title"), "fields" to mapOf("text" to "사람이 쓴 문구"))
        val human = state.withFields(state.fields + ("changes" to listOf(body)))
        val current = original.map { if (it == state) human else it }
        assertThatThrownBy { patch(guards(original), point).apply(current) }.isInstanceOf(EditConflictException::class.java)
        assertThat(points(current, "left").resolve(point).x).isEqualByComparingTo("-5")
        val next = patch(guards(current), point).apply(current)
        assertThat(next.filterNot { it.id == state.id }).isEqualTo(current.filterNot { it.id == state.id })
        val changes = requireNotNull((next.single { it.id == state.id } as FlowDesignBlock).fields["changes"]) as List<*>
        assertThat(changes.first()).isEqualTo(body)
        assertThat(changes.last()).isEqualTo(
            mapOf(
                "op" to "detailSet",
                "target" to target("left").fields(),
                "parts" to point.parts(),
                "field" to "x",
                "value" to 19.toBigDecimal(),
            ),
        )
        assertThat(points(next, "left").resolve(point).x).isEqualByComparingTo("19")
        assertThat(points(next, "left").resolve(point).y).isEqualByComparingTo("2")
        assertThat(points(next, "right").resolve(point).x).isEqualByComparingTo("1")
        assertThat(points(next, "left").resolve(DesignVertexPath("line", "heel")).x).isEqualByComparingTo("7")
        assertThat(patch(guards(next), point).apply(next)).isEqualTo(next)
    }

    @Test
    fun `network vertices use a single keyed step and reject contour or array addresses`() {
        val mesh =
            block(
                "icon01",
                """{"kind":"component","id":"icon","name":"점","props":[],"root":{"id":"root","kind":"box","layout":{"mode":"free"},"children":[
                    {"id":"edge","kind":"vector","network":{"vertices":[{"id":"tip","x":3,"y":4},{"id":"end","x":7,"y":8}],
                    "segments":[{"id":"line","from":"tip","to":"end"}],"regions":[]}}]}}""",
            )
        val current = original.map { if (it == icon) mesh else it }
        val vertex = DesignVertexPath(null, "tip")
        val next = patch(guards(current), vertex).apply(current)
        assertThat(points(next, "left").resolve(vertex).x).isEqualByComparingTo("19")
        assertThat(points(next, "right").resolve(vertex).x).isEqualByComparingTo("3")
        assertThatThrownBy { patch(guards(current), point).apply(current) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { DesignVertexPath.from(listOf(mapOf("collection" to "vertices", "id" to 0))) }
            .isInstanceOf(InvalidDocumentException::class.java)
    }
}
