package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignFields
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignOccurrence
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignScene

/** Only dependencies of the particular occurrence count. Changing another button must not invalidate its thread. */
internal class DesignCommentScene(
    blocks: List<FlowDesignBlock>,
    private val screen: FlowDesignBlock,
    state: FlowDesignBlock?,
) {
    private val catalogue = DesignCommentValues(blocks)
    private val components = blocks.filter { it.kind == "component" }.associateBy { it.designId }
    private val nodes = DesignScene(blocks, screen.id, state?.id).occurrences().toList()
    private val byAddress = nodes.associateBy { it.target }
    private val children = nodes.groupBy { it.parent }
    private val stateChanges = state?.fields?.get("changes").designObjects()

    fun contains(address: DesignAddress): Boolean = address in byAddress

    fun all(): Any = nodes.associate { it.target to appearance(it) }

    fun snapshot(address: DesignAddress): Any? {
        val start = byAddress[address] ?: return null
        val tree = linkedMapOf<DesignAddress, Any>()
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (tree.put(node.target, appearance(node)) == null) pending.addAll(children[node.target].orEmpty())
        }
        return tree to ancestors(start).map(::placement)
    }

    private fun ancestors(start: DesignOccurrence): List<DesignOccurrence> =
        generateSequence(start.parent?.let(byAddress::get)) { it.parent?.let(byAddress::get) }.toList()

    private fun placement(node: DesignOccurrence): Any {
        val layout = fields(node).filterKeys { it in layoutFields }
        val style = fields(node)["style"] as? DesignFields
        return listOf(node.target, layout, style?.filterKeys { it in inheritedStyle })
    }

    private fun appearance(node: DesignOccurrence): Any {
        val fields = fields(node)
        val layout = fields["layout"] as? DesignFields
        val sequence = children[node.target].orEmpty().map { it.target }
        val order = if (layout?.get("mode") == "free") sequence.toSet() else sequence
        // A state may affect a subfield (e.g. the second gradient stop) without replacing the node.
        val overrides = changes(node).filter { it["op"] !in mergedOps }.map { resolve(node, it) }
        return listOf(fields, order, overrides)
    }

    private fun fields(node: DesignOccurrence): Map<*, *> {
        val source: DesignFields = node.node.filterKeys { it !in sourceOnly }
        val edited = changes(node).fold(source) { previous, change -> DesignCommentValues.apply(previous, change) }
        return requireNotNull(resolve(node, edited) as? DesignFields)
    }

    private fun resolve(
        node: DesignOccurrence,
        value: Any?,
    ): Any? {
        val mode =
            (
                node.node["themeMode"] ?: ancestors(node).firstNotNullOfOrNull {
                    it.node["themeMode"]
                } ?: screen.string("themeMode")
            ) as? String
        return catalogue.resolve(value, mode, node.valueOf)
    }

    private fun changes(node: DesignOccurrence): List<DesignFields> {
        val layers =
            node.target.via.indices
                .reversed()
                .flatMap { i -> mountedChanges(node.target, i) }
        return layers + stateChanges.filter { node.target.matches(it["target"]) }
    }

    private fun mountedChanges(
        target: DesignAddress,
        index: Int,
    ): List<DesignFields> {
        val mount = target.via[index]
        val component = components[mount["component"]] ?: return emptyList()
        val parent = DesignAddress(mount.getValue("owner"), mount.getValue("instance"), target.via.take(index))
        val node = byAddress[parent] ?: return emptyList()
        val root = children[parent].orEmpty().firstOrNull { it.target.owner == component.designId } ?: return emptyList()
        val relative = target.copy(via = target.via.drop(index + 1))
        val variants = component.fields["variants"].designObjects().filter { active(it, relative, root) }
        val overrides = node.node["overrides"].designObjects().filter { relative.matches(it["target"]) }
        return variants.map { mapOf("op" to "set", "fields" to it["set"]) } + overrides
    }

    private fun active(
        rule: DesignFields,
        target: DesignAddress,
        root: DesignOccurrence,
    ): Boolean {
        val condition = rule["when"] as? DesignFields ?: return false
        return target.matches(rule["target"]) && root.valueOf(mapOf("prop" to condition["prop"])) == condition["choice"]
    }

    private companion object {
        val sourceOnly = setOf("children", "source", "operands", "slots", "overrides", "locked", "group")
        val mergedOps = setOf("set", "hide", "leaf", "mask", "input", "slot")
        val inheritedStyle = setOf("text", "typography", "transform", "overflow", "opacity")
        val layoutFields =
            setOf(
                "layout",
                "position",
                "size",
                "aspectRatio",
                "flexItem",
                "margin",
                "cell",
                "alignSelf",
                "themeMode",
                "localTransform",
                "visible",
            )
    }
}

internal fun Any?.designObjects(): List<DesignFields> = (this as? List<*>)?.filterIsInstance<DesignFields>().orEmpty()
