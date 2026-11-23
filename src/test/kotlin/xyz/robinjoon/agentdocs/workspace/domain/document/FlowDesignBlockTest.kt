package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockPosition
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DocumentEditor
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader

class FlowDesignBlockTest {
    private val settings =
        design("config", """{"id":"settings","kind":"settings","modelVersion":4,"frame":"phone","idPrefix":"M","nextScreen":3}""")
    private val first = design("first1", """{"kind":"screen","id":"main","code":"M-01","name":"저장","frame":"note"}""")
    private val second = design("second", """{"kind":"screen","id":"other","code":"M-02","name":"Other","frame":"note"}""")

    @Test
    fun `stored fenced markdown keeps server IDs and normalizes nested design JSON`() {
        val indented = """{ "z" : [{"b":true,"a":null}], "kind":"annotation","id":"label","weight":1.00e2,"description":"줄\n둘" }"""
        val source = design("outer1", indented)
        assertThat(
            source.json,
        ).isEqualTo("""{"description":"줄\n둘","id":"label","kind":"annotation","weight":100,"z":[{"a":null,"b":true}]}""")
        val markdown = BlockMarkdown.write(listOf(source), true)
        val reread = MarkdownReader.readBlocks(markdown, "flow").single().block
        assertThat(reread).isEqualTo(source)
        assertThat(BlockMarkdown.write(listOf(reread), true)).isEqualTo(markdown)
        assertThat(MarkdownReader.readBlocks(markdown).single().block).isInstanceOf(CodeBlock::class.java)
    }

    @Test
    fun `fenced design parsing rejects duplicate keys invalid JSON and missing fences`() {
        val invalid =
            listOf(
                """{"id":"a","id":"b","kind":"state"}""",
                """{"kind":"state","id":"one",}""",
                """{"kind":"state","id":"one","n":01}""",
                """{"kind":"state","id":"one","s":"\q"}""",
                """{"kind":"state","id":"one"}[]""",
            )
        invalid.forEach { json ->
            assertThatThrownBy { design("bad001", json) }.isInstanceOf(InvalidDocumentException::class.java)
        }
        assertThatThrownBy { MarkdownReader.readBlocks("```flow-design\n${first.json}", "flow") }
            .isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `design ownership survives moving and deleting a screen`() {
        val note = design("annot1", """{"id":"mark","kind":"annotation","screen":"main","target":{"unresolvedName":"n"}}""")
        val original = listOf(settings, first, second, note)
        FlowRules.validate("flow", original)
        val move = EditOperation.Move(first.id, BlockMarkdown.hash(first), BlockPosition.End)
        val moved = DocumentEditor.apply(original, listOf(move))
        assertThat(moved).containsExactly(settings, second, first, note)
        val removed = DocumentEditor.apply(moved, listOf(EditOperation.Delete(first.id, BlockMarkdown.hash(first))))
        assertThat(removed).containsExactly(settings, second)
        assertThatThrownBy { FlowRules.validate("flow", listOf(settings, second, note)) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `the model cannot mix versions or replace a stable identity`() {
        assertThatThrownBy { FlowRules.validate("flow", listOf(settings, first, FlowRules.initial("phone", "M").last())) }
            .isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { FlowRules.validate("doc", listOf(settings, first)) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { FlowRules.normalize(listOf(settings, second.withId(first.id)), listOf(settings, first)) }
            .isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { FlowRules.normalize(listOf(settings, first.withId(second.id)), listOf(settings, first)) }
            .isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { FlowRules.normalize(FlowRules.initial("phone", "M"), listOf(settings, first)) }
            .isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `large design source is independent of the code block ceiling and screen numbers never move backward`() {
        val large = first.withFields(first.fields + ("description" to "큰".repeat(CodeBlock.MAX_CODE_LENGTH + 1)))
        val source = BlockMarkdown.of(large)
        assertThat(
            MarkdownReader
                .readBlocks(source, "flow")
                .single()
                .block
                .withId(first.id),
        ).isEqualTo(large)
        val previous = settings.withFields(settings.fields + ("nextScreen" to 41))
        val normalized = FlowRules.normalize(listOf(settings, first), listOf(previous, first, second))
        assertThat(normalized.first()).isEqualTo(previous)
    }

    private fun design(
        id: String,
        json: String,
    ): FlowDesignBlock = FlowDesignBlock.parse(BlockId(id), json)
}
