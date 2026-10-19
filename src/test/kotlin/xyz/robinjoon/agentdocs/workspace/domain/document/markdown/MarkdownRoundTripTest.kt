package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ColumnAlignment
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.ListStyle
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.Quote
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableColumn
import xyz.robinjoon.agentdocs.workspace.domain.document.TableRow

class MarkdownRoundTripTest {
    @Test
    fun `annotated markdown reads back as the same blocks with the hashes it declared`() {
        val written = BlockMarkdown.write(SampleBlocks.all, annotated = true)

        val parsed = MarkdownReader.readBlocks(written)

        assertThat(parsed.map { it.block }).isEqualTo(SampleBlocks.all)
        assertThat(parsed.map { it.declaredHash }).isEqualTo(SampleBlocks.all.map(BlockMarkdown::hash))
        assertThat(BlockMarkdown.write(parsed.map { it.block }, annotated = true)).isEqualTo(written)
    }

    @Test
    fun `every block type has one canonical form`() {
        val written = BlockMarkdown.write(SampleBlocks.all, annotated = false)

        assertThat(written).isEqualTo(
            """
            ## Retry **design** for C#

            Uses `exponential` backoff, see [ADR](https://example.com/adr).

            - first
              1. nested
            - [x] done
                - [ ] open

            > line one<br>line two

            > [!WARNING]
            > Mind the quota

            ```mermaid
            graph TD
              A --> B
            ```

            ````markdown
            ```kotlin
            val x = 1
            ```

            ````

            ```
            ```

            | <!--r:row001-->Name | Count |
            | :-- | --: |
            | <!--r:row002-->a \| b |  |

            ![Flow [v2\]](asset:0b6f1c2e-6a8d-4f1e-9d3c-2a4b5c6d7e8f "The \"retry\" flow")

            ---
            """.trimIndent(),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "# not a heading", "1. not a list", "2) not a list", "- not an item", "+ not an item", "> not a quote",
            "---", "***", "![not](an image)", "<!-- not an annotation -->", "| not a table", "```not code", "\\already escaped",
        ],
    )
    fun `a paragraph that looks like another block reads back as the same paragraph`(text: String) {
        val paragraph = Paragraph(BlockId("para01"), InlineText(text))

        val parsed = MarkdownReader.readBlocks(BlockMarkdown.write(listOf(paragraph), annotated = true))

        assertThat(parsed.map { it.block }).containsExactly(paragraph)
    }

    @Test
    fun `item and quote texts that look like a task box or an alert keep their meaning`() {
        val blocks =
            listOf(
                ListItem(BlockId("list01"), ListStyle.BULLET, 0, false, InlineText("[x] not a task")),
                ListItem(BlockId("list02"), ListStyle.NUMBERED, 0, false, InlineText("[X] not a task")),
                Quote(BlockId("quot01"), InlineText("[!NOTE] not an alert")),
            )

        val parsed = MarkdownReader.readBlocks(BlockMarkdown.write(blocks, annotated = true))

        assertThat(parsed.map { it.block }).isEqualTo(blocks)
    }

    @Test
    fun `plain markdown without annotations gets new unique ids`() {
        val parsed = MarkdownReader.readBlocks("# Title\n\nFirst line\ncontinues here\n\n- item\n- [ ] task")

        assertThat(
            parsed.map { BlockMarkdown.of(it.block) },
        ).containsExactly("# Title", "First line continues here", "- item", "- [ ] task")
        assertThat(parsed.map { it.block.id }.toSet()).hasSize(4)
        assertThat(parsed.map { it.declaredHash }).containsOnlyNulls()
    }

    @Test
    fun `a repeated annotation id is a copy and the copy gets a new id`() {
        val parsed = MarkdownReader.readBlocks("<!-- b:para01 h:00000000 -->\nOriginal\n\n<!-- b:para01 h:00000000 -->\nCopy")

        assertThat(parsed.first().block.id).isEqualTo(BlockId("para01"))
        assertThat(parsed.last().block.id).isNotEqualTo(BlockId("para01"))
    }

    @Test
    fun `a table written by hand gets ids for its rows and columns and widens to its longest row`() {
        val parsed = MarkdownReader.readBlocks("| A | B |\n| --- | :-: |\n| 1 | 2 | 3 |\n| 4 |")

        val table = parsed.single().block as TableBlock
        assertThat(table.columns).hasSize(3)
        assertThat(table.rows.map { row -> row.cells.map { it.markdown } }).containsExactly(
            listOf("A", "B", ""),
            listOf("1", "2", "3"),
            listOf("4", "", ""),
        )
        assertThat((table.columns.map { it.id } + table.rows.map { it.id }).toSet()).hasSize(6)
    }

    @Test
    fun `a pipe in a table cell is escaped only in the markdown, so code in the cell keeps its pipe`() {
        val cell = InlineText("`a|b` or c | d")
        val table =
            TableBlock.of(
                BlockId("tabl01"),
                listOf(TableColumn(BlockId("colm01"), ColumnAlignment.NONE)),
                listOf(TableRow.of(BlockId("row001"), listOf(cell))),
            )

        val written = BlockMarkdown.of(table)
        val read = MarkdownReader.readBlocks(written).single().block as TableBlock

        assertThat(written).contains("`a\\|b` or c \\| d")
        assertThat(
            read.rows
                .single()
                .cells
                .single(),
        ).isEqualTo(cell)
        assertThat(
            read.rows
                .single()
                .cells
                .single()
                .plainText(),
        ).isEqualTo("a|b or c | d")
    }

    @Test
    fun `the hash changes with the content and with table column ids`() {
        val table = SampleBlocks.table
        val renamedColumn = TableBlock.of(table.id, listOf(table.columns[0].copy(id = BlockId("colm99")), table.columns[1]), table.rows)

        assertThat(BlockMarkdown.hash(table)).hasSize(8).isEqualTo(BlockMarkdown.hash(TableBlock.of(table.id, table.columns, table.rows)))
        assertThat(BlockMarkdown.hash(renamedColumn)).isNotEqualTo(BlockMarkdown.hash(table))
    }

    @Test
    fun `an image must point at an uploaded asset`() {
        assertThatThrownBy { MarkdownReader.readBlocks("![diagram](https://example.com/a.png)") }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("upload the file first")
    }

    @Test
    fun `a whole document reads its front matter while a fragment reads a leading rule as a divider`() {
        val markdown = "---\ntitle: \"Retry \\\"design\\\"\"\ntags: [payments, retry]\nrevision: 4\n---\n\nBody"

        val document = MarkdownReader.read(markdown)
        val fragment = MarkdownReader.readBlocks("---\n\nBody")

        assertThat(document.frontMatter.text("title")).isEqualTo("Retry \"design\"")
        assertThat(document.frontMatter.list("tags")).containsExactly("payments", "retry")
        assertThat(document.frontMatter.text("revision")).isEqualTo("4")
        assertThat(document.blocks.map { BlockMarkdown.of(it.block) }).containsExactly("Body")
        assertThat(fragment.map { BlockMarkdown.of(it.block) }).containsExactly("---", "Body")
    }
}
