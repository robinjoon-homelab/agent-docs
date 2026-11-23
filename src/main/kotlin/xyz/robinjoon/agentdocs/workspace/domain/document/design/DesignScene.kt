package xyz.robinjoon.agentdocs.workspace.domain.document.design

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock

/** Locates authored nodes in a concrete screen, keeping slot content in its caller's ID scope. */
class DesignScene(
    blocks: List<Block>,
    screenId: BlockId,
    stateId: BlockId?,
) {
    private val all = blocks.filterIsInstance<FlowDesignBlock>()
    private val components = all.filter { it.kind == "component" }.groupBy { it.designId }
    val screen = all.singleOrNull { it.id == screenId && it.kind == "screen" } ?: invalidDesign("screen block not found")
    val state = stateId?.let { id -> all.singleOrNull { it.id == id && it.kind == "state" } ?: invalidDesign("state block not found") }
    internal val changes = state?.fields?.get("changes").objects()

    init {
        if (state != null && state.string("screen") != screen.designId) invalidDesign("state does not belong to this screen")
    }

    fun occurrences(): Sequence<DesignOccurrence> {
        val root = screen.fields["root"] as? DesignFields ?: return emptySequence()
        val scope = DesignScope(screen, dependencies = listOfNotNull(screen, state))
        return walk(root, scope)
    }

    fun resolve(address: DesignAddress): DesignOccurrence {
        val found = occurrences().filter { it.target == address }.take(2).toList()
        return found.singleOrNull() ?: invalidDesign("design address is missing or ambiguous; read nodes again")
    }

    // An outer occurrence can override a nested component. Apply the nearest definition first, then the screen state.
    internal fun detailChanges(target: DesignAddress): Sequence<DesignFields> =
        sequence {
            for (i in target.via.indices.reversed()) {
                val hop = target.via[i]
                val instance = hop["instance"] ?: continue
                val mount = resolve(DesignAddress(hop.getValue("owner"), instance, target.via.take(i)))
                val local = target.copy(via = target.via.drop(i + 1))
                yieldAll(mount.node["overrides"].objects().filter { local.matches(it["target"]) })
            }
            yieldAll(changes.filter { target.matches(it["target"]) })
        }

    private val insertions =
        changes.filter { it["op"] == "insert" }.groupBy {
            val at = it["at"] as? DesignFields
            val address = at?.get("parent") as? DesignFields
            address?.takeIf { target -> "node" in target }?.let(DesignAddress::from)
        }

    private fun inserted(
        target: DesignAddress,
        scope: DesignScope,
    ): Sequence<DesignOccurrence> {
        val owner = state ?: return emptySequence()
        val children = insertions[target].orEmpty().mapNotNull { it["node"] as? DesignFields }
        return children.asSequence().flatMap { walk(it, DesignScope(owner, dependencies = scope.dependencies, parent = target)) }
    }

    private fun walk(
        node: DesignFields,
        scope: DesignScope,
    ): Sequence<DesignOccurrence> =
        sequence {
            val id = node["id"] as? String ?: return@sequence
            when (node["kind"]) {
                "slot" -> {
                    yieldAll(slot(node, scope))
                }

                "repeat" -> {
                    yieldAll(repeat(node, scope))
                }

                else -> {
                    val target = DesignAddress(scope.owner.designId, id, scope.hops)
                    yield(DesignOccurrence(target, scope.dependencies, node, scope.parent, scope::value))
                    yieldAll(children(node, scope.copy(parent = target)))
                    yieldAll(inserted(target, scope))
                    if (node["kind"] == "instance") yieldAll(instance(node, target, scope))
                }
            }
        }

    private fun children(
        node: DesignFields,
        scope: DesignScope,
    ): Sequence<DesignOccurrence> {
        val nested = node["children"].objects() + node["operands"].objects() + listOfNotNull(node["source"] as? DesignFields)
        return nested.asSequence().flatMap { walk(it, scope) }
    }

    private fun instance(
        node: DesignFields,
        target: DesignAddress,
        parent: DesignScope,
    ): Sequence<DesignOccurrence> {
        val name = node["component"] as? String ?: return emptySequence()
        val component = components[name]?.singleOrNull() ?: return emptySequence()
        if (parent.hops.any { it["component"] == name }) return emptySequence()
        val root = component.fields["root"] as? DesignFields ?: return emptySequence()
        val hop = mapOf("owner" to target.owner, "instance" to target.node, "component" to name)
        val assignments = assignments(node, target, parent)
        val scope = DesignScope(component, parent.hops + hop, parent.dependencies + component, assignments, parent = target)
        val assigned = scope.assignments.copy(layers = scope.layers(node, parent))
        return walk(root, scope.copy(assignments = assigned))
    }

    private fun assignments(
        node: DesignFields,
        target: DesignAddress,
        parent: DesignScope,
    ): DesignAssignments {
        val draft = DesignAssignmentDraft(node, parent)
        val stateLayer = state?.let { DesignEditLayer(emptyList(), changes, DesignScope(it, dependencies = parent.dependencies)) }
        for (layer in parent.assignments.layers + listOfNotNull(stateLayer)) {
            layer.forTarget(target).forEach { draft.apply(it, layer.caller) }
        }
        return draft.finish()
    }

    private fun slot(
        node: DesignFields,
        scope: DesignScope,
    ): Sequence<DesignOccurrence> {
        val assigned = scope.assignments.slots[node["prop"]] ?: return node["fallback"].objects().asSequence().flatMap { walk(it, scope) }
        // Slot content belongs to its caller. A template inside the callee still creates a distinct copy in each row.
        val mount = scope.hops.indexOfLast { "instance" in it }
        val repeats = scope.hops.drop(mount + 1).filter { "repeat" in it }
        val caller = assigned.caller.copy(hops = assigned.caller.hops + repeats, dependencies = scope.dependencies, parent = scope.parent)
        return assigned.children.asSequence().flatMap { walk(it, caller) }
    }

    private fun repeat(
        node: DesignFields,
        scope: DesignScope,
    ): Sequence<DesignOccurrence> {
        val items = scope.value(node["items"]).objects()
        val fields =
            scope
                .declaration(node["items"])
                ?.get("fields")
                .objects()
                .associateBy { it["id"] }
        return items.asSequence().flatMap { item ->
            val hop = mapOf("owner" to scope.owner.designId, "repeat" to node["id"].toString(), "itemKey" to item["itemKey"].toString())
            val row = DesignRow(fields, item["fields"].stringFields())
            val next = scope.copy(hops = scope.hops + hop, rows = scope.rows + (hop.getValue("repeat") to row))
            node["template"].objects().asSequence().flatMap { walk(it, next) }
        }
    }
}

data class DesignOccurrence(
    val target: DesignAddress,
    val dependencies: List<FlowDesignBlock>,
    val node: DesignFields,
    val parent: DesignAddress? = null,
    val valueOf: (Any?) -> Any? = { it },
)

private data class DesignAssignments(
    val inputs: Map<String, Any?> = emptyMap(),
    val slots: Map<String, AssignedSlot> = emptyMap(),
    val layers: List<DesignEditLayer> = emptyList(),
)

private data class DesignEditLayer(
    val prefix: List<Map<String, String>>,
    val changes: List<DesignFields>,
    val caller: DesignScope,
) {
    fun forTarget(target: DesignAddress): List<DesignFields> {
        if (target.via.take(prefix.size) != prefix) return emptyList()
        val local = target.copy(via = target.via.drop(prefix.size))
        return changes.filter { local.matches(it["target"]) }
    }
}

private class DesignAssignmentDraft(
    node: DesignFields,
    parent: DesignScope,
) {
    private val inputs = node["inputs"].stringFields().mapValues { parent.input(it.value) }.toMutableMap()
    private val slots = node["slots"].stringFields().mapValues { AssignedSlot(it.value.objects(), parent) }.toMutableMap()

    fun finish() = DesignAssignments(inputs, slots)

    fun apply(
        change: DesignFields,
        caller: DesignScope,
    ) {
        if (change["op"] == "set") {
            val fields = (change["fields"] as? DesignFields)?.get("inputs").stringFields()
            inputs.putAll(fields.mapValues { caller.input(it.value) })
            return
        }
        val prop = change["prop"] as? String ?: return
        when (change["op"]) {
            "input" -> {
                inputs[prop] = caller.input(change["value"])
            }

            "slot" -> {
                assignSlot(prop, change["assignment"], caller)
            }

            "itemSet", "itemMove", "itemInsert", "itemRemove" -> {
                inputs[prop] =
                    DesignRows(change, caller::input).apply(inputs[prop].objects())
            }
        }
    }

    private fun assignSlot(
        prop: String,
        value: Any?,
        caller: DesignScope,
    ) {
        val children = (value as? DesignFields)?.get("children")
        if (children == null) slots.remove(prop) else slots[prop] = AssignedSlot(children.objects(), caller)
    }
}

private data class AssignedSlot(
    val children: List<DesignFields>,
    val caller: DesignScope,
)

private data class DesignRow(
    val declarations: Map<Any?, DesignFields>,
    val values: Map<String, Any?>,
)

private data class DesignScope(
    val owner: FlowDesignBlock,
    val hops: List<Map<String, String>> = emptyList(),
    val dependencies: List<FlowDesignBlock>,
    val assignments: DesignAssignments = DesignAssignments(),
    val rows: Map<String, DesignRow> = emptyMap(),
    val parent: DesignAddress? = null,
) {
    fun layers(
        node: DesignFields,
        caller: DesignScope,
    ): List<DesignEditLayer> {
        val variants =
            owner.fields["variants"]
                .objects()
                .filter { rule ->
                    val condition = rule["when"] as? DesignFields
                    condition != null && value(mapOf("prop" to condition["prop"])) == condition["choice"]
                }.map { mapOf("op" to "set", "target" to it["target"], "fields" to it["set"]) }
        return listOf(DesignEditLayer(hops, variants, this), DesignEditLayer(hops, node["overrides"].objects(), caller)) +
            caller.assignments.layers
    }

    fun input(raw: Any?): Any? {
        if (raw !is List<*>) return value(raw)
        return raw.objects().map { row ->
            row + ("fields" to row["fields"].stringFields().mapValues { input(it.value) })
        }
    }

    fun value(value: Any?): Any? {
        if (value !is DesignFields) return value
        if ("prop" in value) return assignments.inputs[value["prop"]] ?: initial(declaration(value))
        if ("repeat" in value) return rows[value["repeat"]]?.values?.get(value["field"]) ?: initial(declaration(value))
        return value
    }

    fun declaration(binding: Any?): DesignFields? {
        if (binding !is DesignFields) return null
        if ("repeat" in binding) return rows[binding["repeat"]]?.declarations?.get(binding["field"])
        return owner.fields["props"].objects().find { it["id"] == binding["prop"] }
    }

    private fun initial(prop: DesignFields?): Any? =
        prop?.get("initial") ?: when (prop?.get("type")) {
            "boolean" -> false
            "text" -> ""
            "list" -> emptyList<Any>()
            else -> null
        }
}
