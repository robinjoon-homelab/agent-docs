package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignScene
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DesignField
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DesignFieldPatch
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DocumentEditor
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class DesignTargetTest {
    private val settings = block("sett01", """{"id":"settings","kind":"settings","modelVersion":4}""")
    private val screen =
        block(
            "screen01",
            """{"id":"edit","kind":"screen","code":"F-01","name":"편집","root":{"id":"shell","kind":"instance","component":"shell",
            "slots":{"main":[{"id":"phone","kind":"instance","component":"phone"}]}}}""",
        )
    private val shell =
        block(
            "shell001",
            """{"id":"shell","kind":"component","root":{"id":"root","kind":"box","children":[{"id":"slot","kind":"slot","prop":"main"}]}}""",
        )
    private val phone =
        block(
            "phone001",
            """{"id":"phone","kind":"component","root":{"id":"frame","kind":"box","children":[{"kind":"box","id":"scroller","style":{"overflow":"auto"}}]}}""",
        )
    private val address =
        DesignAddress("phone", "scroller", listOf(mapOf("owner" to "edit", "instance" to "phone", "component" to "phone")))
    private val target = DesignJsonWriter.write(address.fields())
    private val state =
        block(
            "state001",
            """{"kind":"state","id":"s56","screen":"edit","description":"원문","changes":[{"op":"hide","target":{"owner":"edit","node":"title"}},
            {"op":"set","target":$target,"fields":{"visible":true,"scroll":{"x":3,"y":0}}}]}""",
        )
    private val blocks = listOf(settings, screen, shell, phone, state)
    private val guards = listOf(screen, shell, phone, state).associate { it.id to BlockMarkdown.hash(it) }

    @Test
    fun `projected slot instance keeps caller ownership and hash guards include the shell`() {
        val scene = DesignScene(blocks, screen.id, state.id)
        val node = scene.resolve(address)
        assertThat(node.dependencies.map { it.id }.distinct()).containsExactlyInAnyOrderElementsOf(guards.keys)
        assertThat(node.parent?.node).isEqualTo("frame")
        val changed = DocumentEditor.apply(blocks, listOf(operation(guards))).filterIsInstance<FlowDesignBlock>()
        assertThat(changed.dropLast(1)).containsExactlyElementsOf(blocks.dropLast(1))
        assertThat(changed.last().json).isEqualTo(state.json.replace("\"y\":0", "\"y\":\"end\""))
    }

    @Test
    fun `stale component hash and omitted shell hash cannot write a state`() {
        val stale = guards + (phone.id to "earlier")
        assertThatThrownBy { DocumentEditor.apply(blocks, listOf(operation(stale))) }.isInstanceOf(EditConflictException::class.java)
        assertThatThrownBy {
            DocumentEditor.apply(
                blocks,
                listOf(operation(guards - shell.id)),
            )
        }.hasMessageContaining("missing dependency hash")
        assertThat(state.json).contains("\"y\":0")
    }

    @Test
    fun `duplicate occurrence and duplicate field declarations require disambiguation`() {
        val repeated =
            phone.withFields(
                phone.fields + (
                    "root" to
                        mapOf(
                            "id" to "frame",
                            "kind" to "box",
                            "children" to
                                List(2) { mapOf("kind" to "box", "id" to "scroller") },
                        )
                ),
            )
        assertThatThrownBy {
            DesignScene(
                blocks - phone + repeated,
                screen.id,
                state.id,
            ).resolve(address)
        }.hasMessageContaining("ambiguous")
        val changes = requireNotNull(state.fields["changes"]) as List<*>
        val repeatedState = state.withFields(state.fields + ("changes" to changes + changes.last()))
        val hashes = guards + (state.id to BlockMarkdown.hash(repeatedState))
        assertThatThrownBy {
            DocumentEditor.apply(
                blocks - state + repeatedState,
                listOf(operation(hashes)),
            )
        }.hasMessageContaining("multiple")
    }

    private fun operation(hashes: Map<BlockId, String>) =
        EditOperation.DesignField(DesignFieldPatch(screen.id, state.id, address, hashes, DesignField("scroll.y", "end")))

    private fun block(
        id: String,
        json: String,
    ): FlowDesignBlock = FlowDesignBlock.parse(BlockId(id), json)
}
