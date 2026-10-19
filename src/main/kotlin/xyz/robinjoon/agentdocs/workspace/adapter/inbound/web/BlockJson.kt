package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
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
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.util.UUID

// The JSON form of a block the web editor sends and receives. Text fields carry inline markdown. Every response block
// also carries its hash, the same one the annotated markdown shows.
object BlockJson {
    fun write(block: Block): Map<String, Any?> =
        mapOf("id" to block.id.value, "type" to typeOf(block), "hash" to BlockMarkdown.hash(block)) + fieldsOf(block)

    fun read(node: JsonNode): Block {
        val type = text(node, "type")
        val reader = readers[type] ?: throw InvalidRequestException("unknown block type $type")
        return reader(BlockId(text(node, "id")), node)
    }

    private val readers: Map<String, (BlockId, JsonNode) -> Block> =
        mapOf(
            "heading" to { id, node -> Heading(id, int(node, "level"), inline(node, "text")) },
            "paragraph" to { id, node -> Paragraph(id, inline(node, "text")) },
            "listItem" to { id, node ->
                ListItem(
                    id,
                    enumOf<ListStyle>(node, "style"),
                    int(node, "depth"),
                    node.path("checked").asBoolean(false),
                    inline(node, "text"),
                )
            },
            "quote" to { id, node -> Quote(id, inline(node, "text")) },
            "callout" to { id, node -> Callout(id, enumOf<CalloutKind>(node, "kind"), inline(node, "text")) },
            "code" to { id, node -> CodeBlock(id, node.path("language").asString(""), text(node, "code")) },
            "table" to ::table,
            "image" to { id, node ->
                ImageBlock(
                    id,
                    AssetId(UUID.fromString(text(node, "assetId"))),
                    node.path("alt").asString(""),
                    node.path("caption").asString(""),
                )
            },
            "divider" to { id, _ -> Divider(id) },
        )

    private fun fieldsOf(block: Block): Map<String, Any?> =
        when (block) {
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
                mapOf("language" to block.language, "code" to block.code)
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

    private fun table(
        id: BlockId,
        node: JsonNode,
    ): TableBlock {
        val columns = node.path("columns").values().map { TableColumn(BlockId(text(it, "id")), enumOf<ColumnAlignment>(it, "align")) }
        val rows =
            node.path("rows").values().map { row ->
                TableRow.of(BlockId(text(row, "id")), row.path("cells").values().map { InlineText(it.asString("").trim()) })
            }
        return TableBlock.of(id, columns, rows)
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
            is Divider -> "divider"
        }

    private fun inline(
        node: JsonNode,
        field: String,
    ): InlineText = InlineText(node.path(field).asString("").trim())

    private fun text(
        node: JsonNode,
        field: String,
    ): String =
        node.get(field)?.takeIf(JsonNode::isString)?.stringValue() ?: throw InvalidRequestException("block field $field is required")

    private fun int(
        node: JsonNode,
        field: String,
    ): Int = node.get(field)?.takeIf(JsonNode::isInt)?.intValue() ?: throw InvalidRequestException("block field $field must be a number")

    private inline fun <reified E : Enum<E>> enumOf(
        node: JsonNode,
        field: String,
    ): E {
        val value = text(node, field)
        return enumValues<E>().firstOrNull { it.wire() == value } ?: throw InvalidRequestException("block field $field cannot be $value")
    }

    private fun Enum<*>.wire(): String = name.lowercase()
}
