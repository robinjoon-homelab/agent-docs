package xyz.robinjoon.agentdocs.workspace.domain.document.history

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ColumnAlignment
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableColumn
import xyz.robinjoon.agentdocs.workspace.domain.document.TableRow

class BlockRestorationTest {
    private val first = paragraph("first1", "First")
    private val middle = paragraph("middle", "Original")
    private val last = paragraph("last01", "Last")

    @Test
    fun `an existing block keeps its current position while other content stays intact`() {
        val changed = paragraph("middle", "Changed")
        val current = listOf(last, changed, first)
        assertThat(BlockRestoration.restore(current, listOf(first, middle, last), middle.id)).containsExactly(last, middle, first)
    }

    @Test
    fun `a deleted block follows the nearest surviving predecessor before trying a successor`() {
        val earlier = listOf(first, paragraph("gone01", "gone"), middle, last)
        assertThat(BlockRestoration.restore(listOf(last, first), earlier, middle.id)).containsExactly(last, first, middle)
        assertThat(BlockRestoration.restore(listOf(last), earlier, middle.id)).containsExactly(middle, last)
        assertThat(BlockRestoration.restore(emptyList(), earlier, middle.id)).containsExactly(middle)
    }

    @Test
    fun `restoring a table preserves the original row and column ids exactly`() {
        val table =
            TableBlock.of(
                BlockId("table1"),
                listOf(TableColumn(BlockId("column"), ColumnAlignment.NONE)),
                listOf(TableRow.of(BlockId("row001"), listOf(InlineText("old")))),
            )
        val restored = BlockRestoration.restore(listOf(first, paragraph("table1", "Changed kind")), listOf(table), table.id)
        assertThat(restored).containsExactly(first, table)
        assertThat((restored.last() as TableBlock).rows.single().id).isEqualTo(BlockId("row001"))
    }

    @Test
    fun `missing historical blocks and duplicate original ids fail explicitly`() {
        assertThatThrownBy {
            BlockRestoration.restore(
                listOf(first),
                listOf(last),
                middle.id,
            )
        }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy {
            BlockRestoration.restore(listOf(first), listOf(middle, middle), middle.id)
        }.isInstanceOf(InvalidDocumentException::class.java)
    }
}
