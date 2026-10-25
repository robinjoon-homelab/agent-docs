package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader

class DocumentEditorTest {
    private val first = paragraph("first1", "First")
    private val second = paragraph("second", "Second has a typo: teh")
    private val third = paragraph("third1", "Third")
    private val blocks = listOf(first, second, third)

    @Test
    fun `a replacement keeps the target id and gives extra blocks ids not used in the document`() {
        val replacement = listOf(paragraph("zzzz", "Rewritten"), paragraph("first1", "Added"), paragraph("second", "Also added"))

        val edited = DocumentEditor.apply(blocks, listOf(EditOperation.Replace(second.id, hash(second), replacement)))

        assertThat(edited.map(::text)).containsExactly("First", "Rewritten", "Added", "Also added", "Third")
        assertThat(edited[1].id).isEqualTo(second.id)
        assertThat(edited.map { it.id }.toSet()).hasSize(5)
    }

    @Test
    fun `every block changed since it was read is reported and nothing is applied`() {
        val operations =
            listOf(
                EditOperation.Insert(BlockPosition.Start, listOf(paragraph("newone", "New"))),
                EditOperation.Delete(first.id, "00000000"),
                EditOperation.Move(third.id, "11111111", BlockPosition.Start),
                EditOperation.Delete(BlockId("gone01"), "22222222"),
            )

        assertThatThrownBy { DocumentEditor.apply(blocks, operations) }
            .isInstanceOfSatisfying(EditConflictException::class.java) { conflict ->
                assertThat(conflict.conflicts).containsExactly(
                    BlockConflict(first.id, hash(first), "First"),
                    BlockConflict(third.id, hash(third), "Third"),
                    BlockConflict(BlockId("gone01"), null, null),
                )
            }
    }

    @Test
    fun `inserts, moves, and deletes apply in order`() {
        val operations =
            listOf(
                EditOperation.Insert(BlockPosition.After(first.id), listOf(paragraph("insert", "Inserted"))),
                EditOperation.Move(third.id, hash(third), BlockPosition.Start),
                EditOperation.Delete(second.id, hash(second)),
                EditOperation.Insert(BlockPosition.End, listOf(paragraph("ending", "End"))),
            )

        assertThat(DocumentEditor.apply(blocks, operations).map(::text)).containsExactly("Third", "First", "Inserted", "End")
    }

    @Test
    fun `a text replacement changes exactly one occurrence inside the block`() {
        val edited = DocumentEditor.apply(blocks, listOf(EditOperation.ReplaceText(second.id, "teh", "the")))

        assertThat(edited[1]).isEqualTo(paragraph("second", "Second has a typo: the"))
    }

    @Test
    fun `text that is gone answers with the block as it is now, and text found twice is rejected`() {
        assertThatThrownBy { DocumentEditor.apply(blocks, listOf(EditOperation.ReplaceText(second.id, "missing", "x"))) }
            .isInstanceOfSatisfying(EditConflictException::class.java) {
                assertThat(it.conflicts).containsExactly(BlockConflict(second.id, BlockMarkdown.hash(second), BlockMarkdown.of(second)))
            }
        assertThatThrownBy { DocumentEditor.apply(blocks, listOf(EditOperation.ReplaceText(second.id, "e", "x"))) }
            .isInstanceOf(InvalidEditException::class.java)
            .hasMessage("operation 0: find text must occur exactly once in block second, found 2")
    }

    @Test
    fun `rewriting a table without its ids keeps the old row and column ids`() {
        val table =
            MarkdownReader
                .readBlocks("<!-- b:tbl001 cols:col001,col002 -->\n| <!--r:row001-->A | B |\n| --- | --- |\n| <!--r:row002-->1 | 3 |")
                .single()
                .block
        val plain = MarkdownReader.readBlocks("| A | B |\n| --- | --- |\n| 1 | 4 |").map { it.block }

        val replacedText = DocumentEditor.apply(listOf(table), listOf(EditOperation.ReplaceText(table.id, "3", "4"))).single() as TableBlock
        val rewritten =
            DocumentEditor
                .apply(
                    listOf(table),
                    listOf(EditOperation.Replace(table.id, hash(table), plain)),
                ).single() as TableBlock

        for (result in listOf(replacedText, rewritten)) {
            assertThat(result.columns.map { it.id.value }).containsExactly("col001", "col002")
            assertThat(result.rows.map { it.id.value }).containsExactly("row001", "row002")
        }
        assertThat(
            rewritten.rows
                .last()
                .cells
                .map { it.markdown },
        ).containsExactly("1", "4")
    }

    @Test
    fun `a table whose replacement starts with another kind of block is rejected, so cell threads keep their cells`() {
        val table = MarkdownReader.readBlocks("<!-- b:tbl001 cols:col001,col002 -->\n| A | B |\n| --- | --- |\n| 1 | 3 |").single().block
        // A cols list written without "b:" is not an annotation, so it reads as a paragraph ahead of the table.
        val strayAnnotation = MarkdownReader.readBlocks("<!-- cols:col001,col002 -->\n| A | B |\n| --- | --- |\n| 1 | 4 |").map { it.block }

        assertThatThrownBy { DocumentEditor.apply(listOf(table), listOf(EditOperation.Replace(table.id, hash(table), strayAnnotation))) }
            .isInstanceOf(InvalidEditException::class.java)
            .hasMessage(
                "operation 0: block tbl001 is a table, so its replacement must start with a table; send the table without an " +
                    "annotation line to keep its row and column ids, or delete it and insert the new blocks",
            )
    }

    @Test
    fun `an operation on a block an earlier operation removed names the operation`() {
        val operations = listOf(EditOperation.Delete(second.id, hash(second)), EditOperation.ReplaceText(second.id, "teh", "the"))

        assertThatThrownBy { DocumentEditor.apply(blocks, operations) }
            .isInstanceOf(InvalidEditException::class.java)
            .hasMessage("operation 1: block second not found")
    }

    private fun paragraph(
        id: String,
        text: String,
    ): Paragraph = Paragraph(BlockId(id), InlineText(text))

    private fun hash(block: Block): String = BlockMarkdown.hash(block)

    private fun text(block: Block): String = block.plainText()
}
