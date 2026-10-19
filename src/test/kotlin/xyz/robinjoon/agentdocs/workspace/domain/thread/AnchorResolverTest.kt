package xyz.robinjoon.agentdocs.workspace.domain.thread

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ColumnAlignment
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableColumn
import xyz.robinjoon.agentdocs.workspace.domain.document.TableRow

class AnchorResolverTest {
    private val anchor = Anchor.Text(BlockId("para01"), 6, 11, "delay")

    @Test
    fun `a range stays where it was while the same text is there`() {
        assertThat(AnchorResolver.resolve(anchor, listOf(paragraph("Retry delay doubles")))).isEqualTo(AnchorResolution.Attached(6, 11))
    }

    @Test
    fun `a range follows its quote to the closest occurrence after the text around it changed`() {
        val edited = paragraph("Always retry, but the delay grows; the delay is capped")

        assertThat(AnchorResolver.resolve(anchor, listOf(edited))).isEqualTo(AnchorResolution.Attached(22, 27))
    }

    @Test
    fun `formatting the quoted text does not move the anchor`() {
        assertThat(AnchorResolver.resolve(anchor, listOf(paragraph("Retry **delay** doubles")))).isEqualTo(AnchorResolution.Attached(6, 11))
    }

    @Test
    fun `a range detaches when its quote or its block is gone`() {
        assertThat(AnchorResolver.resolve(anchor, listOf(paragraph("Retry once")))).isEqualTo(AnchorResolution.Detached)
        assertThat(AnchorResolver.resolve(anchor, emptyList())).isEqualTo(AnchorResolution.Detached)
    }

    @Test
    fun `a rewritten quote moves onto the new words and detaches only when all of it was removed`() {
        val anchored = "Retry delay doubles"

        assertThat(
            AnchorResolver.resolve(anchor, listOf(paragraph("Retry pause doubles")), anchored),
        ).isEqualTo(AnchorResolution.Attached(6, 11))
        assertThat(AnchorResolver.resolve(anchor, listOf(paragraph("Retry wait time doubles")), anchored))
            .isEqualTo(AnchorResolution.Attached(6, 15))
        assertThat(AnchorResolver.resolve(anchor, listOf(paragraph("Retry doubles")), anchored)).isEqualTo(AnchorResolution.Detached)
    }

    @Test
    fun `a cell anchor follows its row and column and detaches when either is removed`() {
        val cell = Anchor.Cell(BlockId("tabl01"), BlockId("row002"), BlockId("colm02"), "3")
        val table = table(listOf("row001", "row002"), listOf("colm01", "colm02"))
        val withoutColumn = table(listOf("row002", "row001"), listOf("colm01"))

        assertThat(AnchorResolver.resolve(cell, listOf(table))).isEqualTo(AnchorResolution.Attached(null, null))
        assertThat(AnchorResolver.resolve(cell, listOf(withoutColumn))).isEqualTo(AnchorResolution.Detached)
    }

    private fun paragraph(text: String): Paragraph = Paragraph(BlockId("para01"), InlineText(text))

    private fun table(
        rows: List<String>,
        columns: List<String>,
    ): TableBlock =
        TableBlock.of(
            BlockId("tabl01"),
            columns.map { TableColumn(BlockId(it), ColumnAlignment.NONE) },
            rows.map { row -> TableRow.of(BlockId(row), columns.map { InlineText("3") }) },
        )
}
