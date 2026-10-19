package xyz.robinjoon.agentdocs.workspace.domain.document.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ColumnAlignment
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.ListStyle
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableColumn
import xyz.robinjoon.agentdocs.workspace.domain.document.TableRow
import java.util.UUID

class RevisionHistoryTest {
    private val header = DocumentHeader(SpaceId(UUID.randomUUID()), DocumentPath("a"), "Title", DocumentProperties.initial)

    @Test
    fun `a moved but unchanged block is not a change and a removed block sits where it was`() {
        val a = paragraph("aaaa", "A")
        val b = paragraph("bbbb", "B")
        val c = paragraph("cccc", "C")

        val changes = BlockDiff.between(listOf(a, b, c), listOf(c, a))

        assertThat(changes.map { it.blockId.value to it.kind }).containsExactly("bbbb" to BlockChangeKind.REMOVED)
    }

    @Test
    fun `a new order of the blocks both sides keep is a change of the document`() {
        val a = paragraph("aaaa", "A")
        val b = paragraph("bbbb", "B")
        val c = paragraph("cccc", "C")

        assertThat(BlockDiff.reordered(listOf(a, b, c), listOf(c, a))).isTrue()
        assertThat(BlockDiff.reordered(listOf(a, b, c), listOf(a, c))).isFalse()
        assertThat(ChangeSummary.of(header, header, emptyList(), reordered = true)).isEqualTo("블록 순서 변경")
    }

    @Test
    fun `the summary names header changes and counts block changes`() {
        val renamed = header.copy(title = "New title", properties = DocumentProperties.of(listOf("x"), DocumentStatus.IN_REVIEW, null))
        val changes =
            BlockDiff.between(
                listOf(paragraph("aaaa", "A"), paragraph("bbbb", "B")),
                listOf(paragraph("aaaa", "A2"), paragraph("cccc", "C")),
            )

        assertThat(ChangeSummary.of(header, renamed, changes)).isEqualTo("제목 변경, 상태 변경, 태그 변경, 블록 1개 추가, 1개 수정, 1개 삭제")
        assertThat(ChangeSummary.of(header, header, emptyList())).isEqualTo("변경 없음")
    }

    @Test
    fun `a table change lists the cells that differ, including a whole new row`() {
        val before = table(listOf("row001" to listOf("a", "b")))
        val after = table(listOf("row001" to listOf("a", "B"), "row002" to listOf("c", "d")))

        val change = BlockDiff.between(listOf(before), listOf(after)).single()

        assertThat(change.changedCells().map { it.rowId.value to it.columnId.value }).containsExactly(
            "row001" to "colm02",
            "row002" to "colm01",
            "row002" to "colm02",
        )
    }

    @Test
    fun `line numbers in the unified diff follow tight lists and multi-line blocks`() {
        val before = listOf(item("item01", "one"), item("item02", "two"), paragraph("para01", "end"))
        val after = listOf(item("item01", "one"), item("item02", "two!"), paragraph("para01", "end"))

        assertThat(
            RevisionDiff.between(1, before, 2, after).unified,
        ).isEqualTo("--- rev 1\n+++ rev 2\n@@ -2,1 +2,1 @@ b:item02\n-- two\n+- two!\n")
    }

    private fun paragraph(
        id: String,
        text: String,
    ): Paragraph = Paragraph(BlockId(id), InlineText(text))

    private fun item(
        id: String,
        text: String,
    ): ListItem = ListItem(BlockId(id), ListStyle.BULLET, 0, false, InlineText(text))

    private fun table(rows: List<Pair<String, List<String>>>): TableBlock =
        TableBlock.of(
            BlockId("tabl01"),
            listOf(TableColumn(BlockId("colm01"), ColumnAlignment.NONE), TableColumn(BlockId("colm02"), ColumnAlignment.NONE)),
            rows.map { (id, cells) -> TableRow.of(BlockId(id), cells.map(::InlineText)) },
        )
}
