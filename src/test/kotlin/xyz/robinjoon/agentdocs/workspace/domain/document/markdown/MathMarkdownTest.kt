package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText

class MathMarkdownTest {
    @Test
    fun `display math preserves formula source IDs and JSON compatible code representation`() {
        val source = "<!-- b:math000001 -->\n$$\na^2 + b^2\n$$"
        val blocks = MarkdownReader.readBlocks(source).map { it.block }

        assertThat(blocks.single()).isInstanceOf(CodeBlock::class.java)
        assertThat(blocks.single().id.value).isEqualTo("math000001")
        assertThat(blocks.single().plainText()).isEqualTo("a^2 + b^2")
        assertThat(BlockMarkdown.write(blocks, annotated = false)).isEqualTo("$$\na^2 + b^2\n$$")
    }

    @Test
    fun `existing fenced math code and literal paragraph delimiters keep their form`() {
        val fenced = "```math\nx^2\n```"
        val code = MarkdownReader.readBlocks(fenced).map { it.block }
        assertThat(BlockMarkdown.write(code, annotated = false)).isEqualTo(fenced)
        val paragraph =
            xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph(
                xyz.robinjoon.agentdocs.workspace.domain.document
                    .BlockId("para000001"),
                InlineText("$$"),
            )
        val restored = MarkdownReader.readBlocks(BlockMarkdown.of(paragraph)).single().block
        assertThat(restored).isEqualTo(paragraph.copy(id = restored.id))
    }

    @Test
    fun `inline formulas preserve literal commands and visible source offsets`() {
        val formula = "값 $\\frac{a*b}{c}$ 와 $${'$'}x^2$$"
        assertThat(InlineText(formula).plainText()).isEqualTo(formula)
    }
}
