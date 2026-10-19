package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Callout
import xyz.robinjoon.agentdocs.workspace.domain.document.CalloutKind
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.ColumnAlignment
import xyz.robinjoon.agentdocs.workspace.domain.document.Divider
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.ListStyle
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.Quote
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableColumn
import xyz.robinjoon.agentdocs.workspace.domain.document.TableRow
import java.util.UUID

// One block of every type, with the characters each markdown form has to escape.
object SampleBlocks {
    val asset = AssetId(UUID.fromString("0b6f1c2e-6a8d-4f1e-9d3c-2a4b5c6d7e8f"))

    val table =
        TableBlock.of(
            BlockId("tabl01"),
            listOf(TableColumn(BlockId("colm01"), ColumnAlignment.LEFT), TableColumn(BlockId("colm02"), ColumnAlignment.RIGHT)),
            listOf(
                TableRow.of(BlockId("row001"), listOf(InlineText("Name"), InlineText("Count"))),
                TableRow.of(BlockId("row002"), listOf(InlineText("a | b"), InlineText.empty)),
            ),
        )

    val all: List<Block> =
        listOf(
            Heading(BlockId("head01"), 2, InlineText("Retry **design** for C#")),
            Paragraph(BlockId("para01"), InlineText("Uses `exponential` backoff, see [ADR](https://example.com/adr).")),
            ListItem(BlockId("list01"), ListStyle.BULLET, 0, false, InlineText("first")),
            ListItem(BlockId("list02"), ListStyle.NUMBERED, 1, false, InlineText("nested")),
            ListItem(BlockId("task01"), ListStyle.TASK, 0, true, InlineText("done")),
            ListItem(BlockId("task02"), ListStyle.TASK, 2, false, InlineText("open")),
            Quote(BlockId("quot01"), InlineText("line one<br>line two")),
            Callout(BlockId("call01"), CalloutKind.WARNING, InlineText("Mind the quota")),
            CodeBlock(BlockId("code01"), "mermaid", "graph TD\n  A --> B"),
            CodeBlock(BlockId("code02"), "markdown", "```kotlin\nval x = 1\n```\n"),
            CodeBlock(BlockId("code03"), "", ""),
            table,
            ImageBlock(BlockId("imag01"), asset, "Flow [v2]", "The \"retry\" flow"),
            Divider(BlockId("divd01")),
        )
}
