package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockPosition
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader

// Edit operations as agents send them. New content is markdown, read with the same reader as whole documents, so an
// annotation in it can keep a block id.
object EditJson {
    fun operations(
        node: JsonNode?,
        type: String = "doc",
    ): List<EditOperation> {
        if (node == null || !node.isArray) throw InvalidRequestException("operations must be an array", "send [{\"op\": \"replace\", ...}]")
        return node.values().mapIndexed { index, operation -> operation(operation, index, type) }
    }

    private fun operation(
        node: JsonNode,
        index: Int,
        type: String,
    ): EditOperation {
        val target = { BlockId(field(node, "blockId", index)) }
        val hash = { field(node, "ifHash", index) }
        return when (val op = node.path("op").asString("")) {
            "replace" -> replacement(node, index, type)
            "insert" -> insertion(node, index, type)
            "delete" -> EditOperation.Delete(target(), hash())
            "move" -> EditOperation.Move(target(), hash(), position(node, index))
            "replaceText" -> EditOperation.ReplaceText(target(), hash(), field(node, "find", index), node.path("replace").asString(""))
            else -> throw InvalidRequestException("operation $index: op must be replace, insert, delete, move, or replaceText, not '$op'")
        }
    }

    private fun insertion(
        node: JsonNode,
        index: Int,
        type: String,
    ): EditOperation.Insert {
        val parsed = MarkdownReader.readBlocks(field(node, "markdown", index), type)
        return EditOperation.Insert(position(node, index), parsed.map { it.block }, parsed.associate { it.block.id to it.flowDefaults })
    }

    private fun replacement(
        node: JsonNode,
        index: Int,
        type: String,
    ): EditOperation.Replace {
        val parsed = MarkdownReader.readBlocks(field(node, "markdown", index), type)
        return EditOperation.Replace(
            BlockId(field(node, "blockId", index)),
            field(node, "ifHash", index),
            parsed.map { it.block },
            parsed.firstOrNull()?.flowDefaults ?: xyz.robinjoon.agentdocs.workspace.domain.document
                .FlowDefaults(),
        )
    }

    private fun position(
        node: JsonNode,
        index: Int,
    ): BlockPosition {
        node.get("after")?.takeIf(JsonNode::isString)?.let { return BlockPosition.After(BlockId(it.stringValue())) }
        node.get("before")?.takeIf(JsonNode::isString)?.let { return BlockPosition.Before(BlockId(it.stringValue())) }
        return when (node.path("at").asString("end")) {
            "start" -> BlockPosition.Start
            "end" -> BlockPosition.End
            else -> throw InvalidRequestException("operation $index: at must be start or end, or pass after or before a block id")
        }
    }

    private fun field(
        node: JsonNode,
        name: String,
        index: Int,
    ): String =
        node.get(name)?.takeIf(JsonNode::isString)?.stringValue() ?: throw InvalidRequestException("operation $index: $name is required")
}
