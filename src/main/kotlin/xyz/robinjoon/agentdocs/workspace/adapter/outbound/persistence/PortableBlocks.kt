package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import xyz.robinjoon.agentdocs.workspace.application.model.FlowBlockData
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Callout
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.Divider
import xyz.robinjoon.agentdocs.workspace.domain.document.DrawingBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.Quote
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

internal object PortableBlocks {
    fun write(block: Block): Map<String, Any?> =
        mapOf("id" to block.id.value, "type" to typeOf(block), "hash" to BlockMarkdown.hash(block)) + fieldsOf(block)

    private fun fieldsOf(block: Block): Map<String, Any?> =
        when (block) {
            is FlowSettings, is FlowScreen, is FlowElement -> {
                FlowBlockData.fields(block)
            }

            is Heading -> {
                mapOf("level" to block.level, "text" to block.text.markdown)
            }

            is Paragraph -> {
                mapOf("text" to block.text.markdown)
            }

            is ListItem -> {
                mapOf(
                    "style" to block.style.wire(),
                    "depth" to block.depth,
                    "checked" to block.checked,
                    "text" to block.text.markdown,
                )
            }

            is Quote -> {
                mapOf("text" to block.text.markdown)
            }

            is Callout -> {
                mapOf("kind" to block.kind.wire(), "text" to block.text.markdown)
            }

            else -> {
                nonTextFieldsOf(block)
            }
        }

    private fun nonTextFieldsOf(block: Block): Map<String, Any?> =
        when (block) {
            is CodeBlock -> {
                mapOf("language" to block.language, "code" to block.code) +
                    if (block.displayMath) mapOf("displayMath" to true) else emptyMap()
            }

            is TableBlock -> {
                mapOf(
                    "columns" to block.columns.map { mapOf("id" to it.id.value, "align" to it.alignment.wire()) },
                    "rows" to rowsOf(block),
                )
            }

            is ImageBlock -> {
                mapOf("assetId" to block.asset.value, "alt" to block.alt, "caption" to block.caption)
            }

            is DrawingBlock -> {
                mapOf("svgAssetId" to block.svg.value, "sceneAssetId" to block.scene.value, "alt" to block.alt)
            }

            else -> {
                emptyMap()
            }
        }

    private fun rowsOf(table: TableBlock): List<Map<String, Any>> =
        table.rows.map { row ->
            mapOf(
                "id" to row.id.value,
                "cells" to row.cells.map { it.markdown },
            )
        }

    private fun typeOf(block: Block): String =
        when (block) {
            is Heading -> "heading"
            is Paragraph -> "paragraph"
            is ListItem -> "listItem"
            is Quote -> "quote"
            is Callout -> "callout"
            is CodeBlock -> "code"
            is TableBlock -> "table"
            is ImageBlock -> "image"
            is DrawingBlock -> "drawing"
            is Divider -> "divider"
            is FlowSettings, is FlowScreen, is FlowElement -> FlowBlockData.type(block)
        }

    private fun Enum<*>.wire(): String = name.lowercase()
}
