package xyz.robinjoon.agentdocs.workspace.domain.thread

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader

class SuggestionPatchTest {
    private val block = Paragraph(BlockId("intro1"), InlineText("hello **world** and world"))

    @Test
    fun `changing an annotation description preserves drawing identity and requires its viewed state`() {
        val annotation = FlowDesignBlock.parse(BlockId("annot1"), """{"kind":"annotation","id":"a","screen":"s","description":"old"}""")
        val state = FlowDesignBlock.parse(BlockId("state1"), """{"kind":"state","id":"on","screen":"s"}""")
        val basis = SuggestionBasis(Anchor.Whole(annotation.id, "", state.id), BlockMarkdown.hash(annotation), "old", "new")
        val changed = SuggestionPatch.apply(listOf(annotation, state), basis).filterIsInstance<FlowDesignBlock>()
        assertThat(changed.first()).isEqualTo(annotation.withFields(annotation.fields + ("description" to "new")))
        assertThat(changed.last()).isEqualTo(state)
        assertThatThrownBy { SuggestionPatch.apply(listOf(annotation), basis) }.isInstanceOf(EditConflictException::class.java)
    }

    @Test
    fun `whole table suggestions reject replacement by a paragraph`() {
        val table = MarkdownReader.readBlocks("| A | B |\n| --- | --- |\n| one | two |").single().block
        val basis =
            SuggestionBasis(Anchor.Whole(table.id, table.plainText()), BlockMarkdown.hash(table), BlockMarkdown.of(table), "paragraph")
        assertThatThrownBy { SuggestionPatch.apply(listOf(table), basis) }.isInstanceOf(InvalidThreadException::class.java)
    }

    @Test
    fun `whole table suggestions retain row and column identifiers`() {
        val table =
            MarkdownReader
                .readBlocks(
                    "| A | B |\n| --- | --- |\n| one | two |",
                ).single()
                .block as xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
        val before = BlockMarkdown.of(table)
        val basis =
            SuggestionBasis(Anchor.Whole(table.id, table.plainText()), BlockMarkdown.hash(table), before, before.replace("two", "changed"))
        val result = SuggestionPatch.apply(listOf(table), basis).single() as xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
        assertThat(result.id).isEqualTo(table.id)
        assertThat(result.columns.map { it.id }).isEqualTo(table.columns.map { it.id })
        assertThat(result.rows.map { it.id }).isEqualTo(table.rows.map { it.id })
        assertThat(
            result.rows
                .last()
                .cells
                .last()
                .markdown,
        ).isEqualTo("changed")
    }

    @Test
    fun `text range replaces only its occurrence and keeps other blocks and formatting`() {
        val other = Paragraph(BlockId("other1"), InlineText("unchanged"))
        val basis = SuggestionBasis(Anchor.Text(block.id, 16, 21, "world"), BlockMarkdown.hash(block), "world", "friend")
        val result = SuggestionPatch.apply(listOf(block, other), basis)
        assertThat(BlockMarkdown.of(result.first())).isEqualTo("hello **world** and friend")
        assertThat(result.last()).isEqualTo(other)
        assertThat(result.first().id).isEqualTo(block.id)
    }

    @Test
    fun `a stale hash stops the complete suggestion`() {
        val basis = SuggestionBasis(Anchor.Whole(block.id, block.plainText()), "stale", BlockMarkdown.of(block), "new")
        assertThatThrownBy { SuggestionPatch.apply(listOf(block), basis) }.isInstanceOf(EditConflictException::class.java)
    }

    @Test
    fun `cell suggestion preserves table row and column identifiers`() {
        val table =
            MarkdownReader
                .readBlocks(
                    "| A | B |\n| --- | --- |\n| one | two |",
                ).single()
                .block as xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
        val row = table.rows.last()
        val basis =
            SuggestionBasis(Anchor.Cell(table.id, row.id, table.columns.last().id, "two"), BlockMarkdown.hash(table), "two", "three")
        val changed = SuggestionPatch.apply(listOf(table), basis).single() as xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
        assertThat(changed.columns).isEqualTo(table.columns)
        assertThat(changed.rows.map { it.id }).isEqualTo(table.rows.map { it.id })
        assertThat(
            changed.rows
                .last()
                .cells
                .map { it.markdown },
        ).containsExactly("one", "three")
    }
}
