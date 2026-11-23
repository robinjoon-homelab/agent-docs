package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignFields
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignOccurrence
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignScene
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignVertexPath
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignVertices
import xyz.robinjoon.agentdocs.workspace.domain.document.design.invalidDesign
import xyz.robinjoon.agentdocs.workspace.domain.document.design.stringFields
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.math.BigDecimal

class DesignFieldPatch(
    val screen: BlockId,
    val state: BlockId,
    val target: DesignAddress,
    guards: Map<BlockId, String>,
    val field: DesignField,
) {
    val guards: Map<BlockId, String> = guards.toMap()

    fun apply(blocks: List<Block>): List<Block> {
        checkHashes(blocks)
        val scene = DesignScene(blocks, screen, state)
        val occurrence = scene.resolve(target)
        if (occurrence.dependencies.any { it.id !in guards }) invalidDesign("missing dependency hash; read nodes with screen and state")
        field.validate(occurrence, scene)
        val block = requireNotNull(scene.state)
        val changes = patchChanges(scene.changes)
        val updated = block.withFields(block.fields + ("changes" to changes))
        return blocks.map { if (it.id == state) updated else it }
    }

    private fun checkHashes(blocks: List<Block>) {
        val byId = blocks.associateBy { it.id }
        val conflicts =
            guards.mapNotNull { (id, expected) ->
                val current = byId[id]?.let(BlockMarkdown::hash)
                if (current == expected) null else BlockConflict(id, current, null)
            }
        if (conflicts.isNotEmpty()) throw EditConflictException(conflicts)
    }

    private fun patchChanges(changes: List<DesignFields>): List<DesignFields> {
        val writes = changes.withIndex().filter { target.matches(it.value["target"]) && field.touchedBy(it.value) }
        if (writes.size > 1) invalidDesign("multiple changes write this field; edit the state explicitly")
        val existing = writes.singleOrNull() ?: return changes + field.set(target)
        val updated = field.replace(existing.value)
        return changes.mapIndexed { index, change -> if (index == existing.index) updated else change }
    }
}

class DesignField(
    val path: String,
    val value: Any,
    val vertex: DesignVertexPath? = null,
) {
    private val keys = path.split('.')

    init {
        val valid =
            when {
                vertex != null -> {
                    path in setOf("x", "y") && value is BigDecimal && value.toDouble().isFinite()
                }

                path == "visible" -> {
                    value is Boolean
                }

                path == "text" -> {
                    value is String
                }

                path in setOf("scroll.x", "scroll.y") -> {
                    value == "end" ||
                        (value is BigDecimal && value.signum() >= 0 && value.toDouble().isFinite())
                }

                else -> {
                    false
                }
            }
        if (!valid) invalidDesign("invalid path/value: use text, visible, scroll.x/y, or finite x/y on a vertex ID")
    }

    fun validate(
        occurrence: DesignOccurrence,
        scene: DesignScene,
    ) {
        if (vertex != null) {
            DesignVertices(occurrence, scene).resolve(vertex)
            return
        }
        val node = occurrence.node
        if (path == "text" && node["kind"] != "text") invalidDesign("text requires a text node")
        if (keys.first() != "scroll") return
        val overflow = (node["style"] as? DesignFields)?.get("overflow")
        val axis = if (overflow is Map<*, *>) overflow[keys.last()] else overflow
        if (node["kind"] != "box" || axis !in setOf("scroll", "auto")) invalidDesign("target axis does not own scroll")
    }

    internal fun touchedBy(change: DesignFields): Boolean {
        if (vertex != null) return change["op"] == "detailSet" && change["parts"] == vertex.parts() && change["field"] == path
        if (change["op"] == "leaf" || change["op"] == "mask") return change["path"] == path || change["path"] == keys.first()
        if (change["op"] == "hide") return path == "visible"
        if (change["op"] != "set") return false
        val fields = change["fields"].stringFields()
        if (keys.size == 1) return path in fields
        return keys.last() in fields[keys.first()].stringFields()
    }

    internal fun set(target: DesignAddress): Map<String, Any?> {
        if (vertex !=
            null
        ) {
            return mapOf(
                "op" to "detailSet",
                "target" to target.fields(),
                "parts" to vertex.parts(),
                "field" to path,
                "value" to value,
            )
        }
        return mapOf("op" to "set", "target" to target.fields(), "fields" to updated(emptyMap()))
    }

    internal fun replace(change: DesignFields): Map<String, Any?> {
        if (vertex != null) return change.stringFields() + ("value" to value)
        if (change["op"] == "leaf" && change["path"] == path) return change.stringFields() + ("value" to value)
        if (change["op"] != "set") invalidDesign("field is controlled by another operation; edit the state explicitly")
        return change.stringFields() + ("fields" to updated(change["fields"].stringFields()))
    }

    private fun updated(fields: Map<String, Any?>): Map<String, Any?> {
        if (keys.size == 1) return fields + (path to value)
        val nested = fields[keys.first()].stringFields() + (keys.last() to value)
        return fields + (keys.first() to nested)
    }
}
