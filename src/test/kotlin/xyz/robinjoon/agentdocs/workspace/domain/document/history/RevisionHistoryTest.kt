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
    fun `moved blocks and removed blocks appear in target order and next to their old predecessor`() {
        val a = paragraph("aaaa", "A")
        val b = paragraph("bbbb", "B")
        val c = paragraph("cccc", "C")

        val changes = BlockDiff.between(listOf(a, b, c), listOf(c, a))

        assertThat(changes.map { it.blockId.value to it.kind }).containsExactly(
            "cccc" to BlockChangeKind.MOVED,
            "aaaa" to BlockChangeKind.MOVED,
            "bbbb" to BlockChangeKind.REMOVED,
        )
    }

    @Test
    fun `a new order of the blocks both sides keep is a change of the document`() {
        val a = paragraph("aaaa", "A")
        val b = paragraph("bbbb", "B")
        val c = paragraph("cccc", "C")

        assertThat(BlockDiff.reordered(listOf(a, b, c), listOf(c, a))).isTrue()
        assertThat(BlockDiff.reordered(listOf(a, b, c), listOf(a, c))).isFalse()
        val diff = RevisionDiff.between(1, listOf(a, b, c), 2, listOf(c, a, b))
        assertThat(diff.changedBlocks).containsExactly(c.id, a.id, b.id)
        assertThat(diff.unified).contains("b:cccc", "b:aaaa", "b:bbbb")
        assertThat(ChangeSummary.of(header, header, diff.changes)).isEqualTo("블록 순서 변경")
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

    @Test
    fun `append and remove a paragraph with its blank separator`() {
        val first = paragraph("aaaa", "alpha")
        val next = paragraph("bbbb", "beta")
        assertThat(RevisionDiff.between(1, listOf(first), 2, listOf(first, next)).unified)
            .endsWith("@@ -1,0 +2,2 @@ b:bbbb\n+\n+beta\n")
        assertThat(RevisionDiff.between(2, listOf(first, next), 3, listOf(first)).unified)
            .endsWith("@@ -2,2 +1,0 @@ b:bbbb\n-\n-beta\n")
    }

    @Test
    fun `first addition and last removal use empty ranges at line zero`() {
        val only = listOf(paragraph("aaaa", "alpha"))
        assertThat(RevisionDiff.between(1, emptyList(), 2, only).unified)
            .endsWith("@@ -0,0 +1,1 @@ b:aaaa\n+alpha\n")
        assertThat(RevisionDiff.between(2, only, 3, emptyList()).unified)
            .endsWith("@@ -1,1 +0,0 @@ b:aaaa\n-alpha\n")
    }

    @Test
    fun `removing a paragraph between list items removes both separating blank lines`() {
        val first = item("item01", "one")
        val last = item("item02", "two")
        val spaced = listOf(first, paragraph("para01", "between"), last)
        assertThat(RevisionDiff.between(1, spaced, 2, listOf(first, last)).unified)
            .endsWith("@@ -2,3 +1,0 @@ b:para01\n-\n-between\n-\n")
    }

    @Test
    fun `blocks moving across an unchanged block use nonoverlapping hunks in file order`() {
        val a = paragraph("aaaa", "first")
        val b = paragraph("bbbb", "middle")
        val c = paragraph("cccc", "last")
        assertThat(RevisionDiff.between(1, listOf(a, b, c), 2, listOf(c, b, a)).unified)
            .isEqualTo(
                "--- rev 1\n+++ rev 2\n" +
                    "@@ -1,1 +1,1 @@ b:aaaa b:cccc\n-first\n+last\n" +
                    "@@ -5,1 +5,1 @@ b:cccc b:aaaa\n-last\n+first\n",
            )
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
