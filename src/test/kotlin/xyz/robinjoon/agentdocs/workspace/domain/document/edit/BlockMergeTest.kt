package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class BlockMergeTest {
    private val first = paragraph("first1", "First")
    private val second = paragraph("second", "Second")
    private val third = paragraph("third1", "Third")
    private val base = listOf(first, second, third)

    @Test
    fun `changes to different blocks are both kept`() {
        val theirs = listOf(first, paragraph("second", "Second, edited by them"), third)
        val mine = listOf(paragraph("first1", "First, edited by me"), second, third)

        val merged = BlockMerge.merge(base, theirs, mine)

        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.blocks.map(Block::plainText)).containsExactly("First, edited by me", "Second, edited by them", "Third")
    }

    @Test
    fun `different changes to the same block conflict and report the current block`() {
        val theirs = listOf(first, paragraph("second", "Their second"), third)
        val mine = listOf(first, paragraph("second", "My second"), third)

        val merged = BlockMerge.merge(base, theirs, mine)

        assertThat(merged.conflicts).containsExactly(BlockConflict(second.id, BlockMarkdown.hash(theirs[1]), "Their second"))
    }

    @Test
    fun `the same change on both sides is not a conflict`() {
        val edited = listOf(first, paragraph("second", "Same edit"), third)

        val merged = BlockMerge.merge(base, edited, edited)

        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.blocks).isEqualTo(edited)
    }

    @Test
    fun `a deletion on one side wins over an untouched block and conflicts with a changed one`() {
        val untouchedDeletion = BlockMerge.merge(base, listOf(first, third), base)
        val myDeletionOfTheirEdit = BlockMerge.merge(base, listOf(first, paragraph("second", "Their second"), third), listOf(first, third))
        val theirDeletionOfMyEdit = BlockMerge.merge(base, listOf(first, third), listOf(first, paragraph("second", "My second"), third))

        assertThat(untouchedDeletion.blocks).containsExactly(first, third)
        assertThat(untouchedDeletion.conflicts).isEmpty()
        assertThat(myDeletionOfTheirEdit.conflicts.map { it.blockId }).containsExactly(second.id)
        assertThat(theirDeletionOfMyEdit.conflicts).containsExactly(BlockConflict(second.id, null, null))
    }

    @Test
    fun `blocks others added stay after the block that precedes them`() {
        val added = paragraph("added1", "Added by them")
        val theirs = listOf(first, added, second, third)
        val mine = listOf(first, third, paragraph("mine01", "Added by me"))

        val merged = BlockMerge.merge(base, theirs, mine)

        assertThat(merged.conflicts).isEmpty()
        assertThat(merged.blocks.map(Block::plainText)).containsExactly("First", "Added by them", "Third", "Added by me")
    }

    private fun paragraph(
        id: String,
        text: String,
    ): Paragraph = Paragraph(BlockId(id), InlineText(text))
}
