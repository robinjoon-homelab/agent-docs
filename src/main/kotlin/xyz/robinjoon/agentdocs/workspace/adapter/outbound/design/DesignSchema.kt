package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import com.networknt.schema.OutputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.path.NodePath
import com.networknt.schema.path.PathType
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

internal object DesignSchema {
    private val root = load()
    private val block = definition("Block")
    private val box = definition("Box")
    private val node = definition("DesignNode")
    private val child = definition("Child")
    private val change = definition("Change")

    fun validate(document: JsonNode) {
        requireFiniteNumbers(document)
        if (root.validate(document, OutputFormat.BOOLEAN)) return
        val blocks = document.path("blocks")
        val index = blocks.values().indexOfFirst { !block.validate(it, OutputFormat.BOOLEAN) }
        val location = if (index < 0) "/" else invalidBlock(blocks.path(index), "/blocks/$index")
        throw InvalidDocumentException("invalid flow-design v4 schema at $location")
    }

    fun validateNode(value: JsonNode) {
        if (!node.validate(value, OutputFormat.BOOLEAN)) {
            throw InvalidDocumentException("invalid effective design node at ${invalidNode(value, "/target")}")
        }
    }

    // Values outside the renderer's numerical range cannot have the same meaning on the server and client.
    private fun requireFiniteNumbers(value: JsonNode) {
        val remaining = ArrayDeque<JsonNode>()
        remaining.add(value)
        while (remaining.isNotEmpty()) {
            val current = remaining.removeLast()
            if (current.isNumber && !current.doubleValue().isFinite()) {
                throw InvalidDocumentException("flow-design numbers must be finite")
            }
            remaining.addAll(current.values())
        }
    }

    private fun invalidBlock(
        value: JsonNode,
        path: String,
    ): String {
        val rootNode = value.get("root")
        if (rootNode != null && !box.validate(rootNode, OutputFormat.BOOLEAN)) return invalidNode(rootNode, "$path/root")
        val index = value.path("changes").values().indexOfFirst { !change.validate(it, OutputFormat.BOOLEAN) }
        if (index < 0) return label(value, path)
        val operation = value.path("changes").path(index)
        return "$path/changes/$index (${operation.path("op").asString("")} ${operation.path("target").path("node").asString("")})"
    }

    private tailrec fun invalidNode(
        value: JsonNode,
        path: String,
    ): String {
        val branch = invalidChild(value, path) ?: return label(value, path)
        return invalidNode(branch.second, branch.first)
    }

    private fun invalidChild(
        value: JsonNode,
        path: String,
    ): Pair<String, JsonNode>? {
        for (field in listOf("children", "fallback", "template", "operands")) {
            val section = value.path(field)
            val index = section.values().indexOfFirst { !child.validate(it, OutputFormat.BOOLEAN) }
            if (index >= 0) return "$path/$field/$index" to section.path(index)
        }
        value.get("source")?.takeUnless { node.validate(it, OutputFormat.BOOLEAN) }?.let { return "$path/source" to it }
        for ((slot, items) in value.path("slots").properties()) {
            val index = items.values().indexOfFirst { !child.validate(it, OutputFormat.BOOLEAN) }
            if (index >= 0) return "$path/slots/${slot.replace("~", "~0").replace("/", "~1")}/$index" to items.path(index)
        }
        return null
    }

    private fun label(
        value: JsonNode,
        path: String,
    ): String {
        val kind = value.path("kind").asString("")
        val id = value.path("id").asString("")
        return "$path ($kind $id)"
    }

    private fun definition(name: String): Schema = root.getSubSchema(NodePath(PathType.JSON_POINTER).append("\$defs").append(name))

    // The trusted schema only has fragment references. No document can provide a schema location.
    private fun load(): Schema {
        val resource = "/flow-design/design-model-v4.schema.json"
        val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        return checkNotNull(DesignSchema::class.java.getResourceAsStream(resource)).use { registry.getSchema(it) }
    }
}
