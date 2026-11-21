package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAction
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAppearance
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowBehavior
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowControls
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDescription
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowFrame
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowPlacement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings

object FlowBlockJson {
    fun settings(
        id: BlockId,
        node: JsonNode,
    ): Block = FlowSettings(id, text(node, "frame"), text(node, "idPrefix"), int(node, "nextScreen"))

    fun screen(
        id: BlockId,
        node: JsonNode,
    ): Block =
        FlowScreen(
            id,
            text(node, "screenId"),
            text(node, "name"),
            text(node, "entry"),
            FlowFrame(optional(node, "frame"), optionalInt(node, "frameW"), optionalInt(node, "frameH")),
            text(node, "desc"),
        )

    fun element(
        id: BlockId,
        node: JsonNode,
    ): Block =
        FlowElement(
            id,
            FlowAppearance.of(text(node, "kind"), optional(node, "look"), strings(node, "items"), optional(node, "date")),
            FlowPlacement(int(node, "x"), int(node, "y"), int(node, "w"), int(node, "h")),
            FlowDescription(text(node, "label"), text(node, "value"), text(node, "desc")),
            behavior(node),
        )

    private fun behavior(node: JsonNode): FlowBehavior {
        val actions = array(node, "actions").map { FlowAction(text(it, "when"), optional(it, "to"), text(it, "mode")) }
        return FlowBehavior.of(
            FlowControls(bool(node, "pinned"), optional(node, "group"), bool(node, "annotated")),
            actions,
            strings(node, "states").orEmpty(),
        )
    }

    private fun optional(
        node: JsonNode,
        key: String,
    ): String? = node.get(key)?.takeUnless { it.isNull }?.let { text(node, key) }

    private fun text(
        node: JsonNode,
        key: String,
    ): String =
        node.get(key)?.takeIf { it.isString }?.stringValue()
            ?: throw InvalidRequestException("flow $key must be a string")

    private fun int(
        node: JsonNode,
        key: String,
    ): Int =
        node.get(key)?.takeIf { it.isInt }?.intValue()
            ?: throw InvalidRequestException("flow $key must be an integer")

    private fun optionalInt(
        node: JsonNode,
        key: String,
    ): Int? = node.get(key)?.takeUnless { it.isNull }?.let { int(node, key) }

    private fun bool(
        node: JsonNode,
        key: String,
    ): Boolean =
        node.get(key)?.takeIf { it.isBoolean }?.booleanValue()
            ?: throw InvalidRequestException("flow $key must be a boolean")

    private fun array(
        node: JsonNode,
        key: String,
    ): List<JsonNode> =
        node
            .get(key)
            ?.takeIf { it.isArray }
            ?.values()
            ?.toList()
            ?: throw InvalidRequestException("flow $key must be an array")

    private fun strings(
        node: JsonNode,
        key: String,
    ): List<String>? {
        if (node.get(key) == null || node.path(key).isNull) return null
        return array(node, key).map {
            it.takeIf { value -> value.isString }?.stringValue()
                ?: throw InvalidRequestException("flow $key must contain strings")
        }
    }
}
