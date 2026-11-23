package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory

internal class DesignProjectionEdits(
    private val tree: DesignProjectionTree,
    private val rules: DesignInputRules,
) {
    fun apply(edit: DesignProjectionEdit) {
        val op = edit.change
        val fact = rules.facts.track(edit.site, op)
        if (op.designString("op") == "insert" || op.designString("op") == "move") {
            place(edit, fact)
            return
        }
        if (!op.path("target").has("node")) return
        val target = tree.find(edit.target(), fact) ?: return
        when (op.designString("op")) {
            "set" -> fields(target, edit, fact)
            "leaf", "mask" -> leaf(target, edit, fact)
            "run" -> target.patch.run(op) { rules.facts.missing(fact, it) }
            in inputOperations -> assignment(target, edit, fact)
            else -> if (op.designString("op").startsWith("detail")) detail(target, edit, fact)
        }
    }

    private fun detail(
        rec: DesignOccurrence,
        edit: DesignProjectionEdit,
        fact: DesignInputFact,
    ) {
        DesignGraphDetails.apply(rec.patch.writable(), edit.change) { rules.facts.missing(fact, it) }
    }

    private fun leaf(
        rec: DesignOccurrence,
        edit: DesignProjectionEdit,
        fact: DesignInputFact,
    ) {
        val path = edit.change.designString("path")
        val value = edit.change.get("value")
        if (path.startsWith("style.radius.")) tree.resolveRadius(rec)
        rec.patch.leaf(path, value)
        if (value?.isDesignBinding() == true) {
            val root = JsonNodeFactory.instance.objectNode()
            var field = root
            val keys = path.split('.')
            keys.dropLast(1).forEach { field = field.withObjectProperty(it) }
            field.set(keys.last(), value)
            rules.visual(root, edit.scope.env, fact.site)
        }
    }

    private fun fields(
        rec: DesignOccurrence,
        edit: DesignProjectionEdit,
        fact: DesignInputFact,
    ) {
        val fields = edit.change.path("fields")
        rules.visual(fields, edit.scope.env, fact.site)
        if (fields.path("style").has("radius")) tree.resolveRadius(rec)
        rec.patch.fields(fields)
        val assigned = fields.get("inputs") ?: return
        assigned.properties().forEach { (key, value) -> input(rec, key, value, edit to fact) }
        tree.refresh(rec)
    }

    private fun assignment(
        rec: DesignOccurrence,
        edit: DesignProjectionEdit,
        fact: DesignInputFact,
    ) {
        if (rec.source.designString("kind") != "instance") return rules.facts.invalid("input and slot edits require an instance")
        if (rec.component == null) return
        val id = edit.change.designString("prop")
        val prop = rec.props[id] ?: return rules.facts.missing(fact, "unknown component property: $id")
        when (edit.change.designString("op")) {
            "input" -> input(rec, id, edit.change.path("value"), edit to fact)
            "slot" -> slot(rec, prop, edit)
            else -> DesignInputList(rules, edit, fact).apply(rec, prop)
        }
        tree.refresh(rec)
    }

    private fun input(
        rec: DesignOccurrence,
        key: String,
        value: JsonNode,
        context: Pair<DesignProjectionEdit, DesignInputFact>,
    ) {
        val (edit, fact) = context
        if (rec.source.designString("kind") != "instance") return rules.facts.invalid("input edits require an instance")
        val prop = rec.props[key] ?: return rules.facts.missing(fact, "undeclared input: $key")
        rules.input(prop, value, edit.scope.env, fact)
        edit.scope.env
            .resolve(value)
            ?.let { rec.inputs[key] = it.deepCopy() }
    }

    private fun slot(
        rec: DesignOccurrence,
        prop: JsonNode,
        edit: DesignProjectionEdit,
    ) {
        if (prop.designString("type") != "slot") return rules.facts.invalid("expected a slot property")
        val id = prop.designString("id")
        val nodes = edit.change.path("assignment").get("children")
        if (nodes == null) rec.slots.remove(id) else rec.slots[id] = DesignSlotContent(nodes, edit.scope)
    }

    private fun place(
        edit: DesignProjectionEdit,
        fact: DesignInputFact,
    ) {
        val at = edit.change.path("at")
        val parent = tree.find(DesignOccurrenceAddress.from(at.path("parent"), edit.scope.via), fact) ?: return
        val sibling = at.get("before") ?: at.get("after")
        val adjacent = sibling?.let { tree.find(DesignOccurrenceAddress.from(it, edit.scope.via), fact) }
        if (sibling != null && adjacent == null) return
        if (adjacent != null &&
            adjacent.parent !== parent
        ) {
            return rules.facts.missing(fact, "ordering sibling is not a child of the destination")
        }
        if (parent.source.designString("kind") !in containers) return rules.facts.invalid("destination cannot contain children")
        val index = destination(at, parent, adjacent)
        val layer = moving(edit, parent, fact) ?: return
        if (layer === adjacent) return rules.facts.invalid("cannot place a node beside itself")
        val old = parent.children.indexOf(layer)
        layer.parent?.children?.remove(layer)
        layer.parent = parent
        parent.children.add(if (old in 0 until index) index - 1 else index, layer)
    }

    private fun moving(
        edit: DesignProjectionEdit,
        parent: DesignOccurrence,
        fact: DesignInputFact,
    ): DesignOccurrence? {
        if (edit.change.has("node")) return tree.emit(edit.change.path("node"), edit.scope, null)
        val source = tree.find(edit.target(), fact) ?: return null
        if (source.ancestorOf(parent)) {
            rules.facts.invalid("cannot move a node under its descendant")
            return null
        }
        return source
    }

    private fun destination(
        at: JsonNode,
        parent: DesignOccurrence,
        adjacent: DesignOccurrence?,
    ): Int {
        if (adjacent == null) return if (at.designString("edge") == "first") 0 else parent.children.size
        return parent.children.indexOf(adjacent) + if (at.has("after")) 1 else 0
    }

    companion object {
        val inputOperations = setOf("input", "slot", "itemSet", "itemInsert", "itemRemove", "itemMove")
        private val containers = setOf("box", "group", "mask", "boolean")
    }
}
