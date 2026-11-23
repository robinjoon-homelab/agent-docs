package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import java.util.Collections
import java.util.IdentityHashMap

internal class DesignProjectionTree(
    private val blocks: Map<String, JsonNode>,
    val rules: DesignInputRules,
) {
    private val index = HashMap<DesignOccurrenceAddress, MutableSet<DesignOccurrence>>()
    private val active = Collections.newSetFromMap(IdentityHashMap<JsonNode, Boolean>())
    private val edits = DesignProjectionEdits(this, rules)
    private val library = blocks.values.firstOrNull { it.designString("kind") == "tokens" }
    private val tokenRules = DesignTokenRules(library)
    private val tokens = library?.path("entries")?.associateBy { it.designString("id") }.orEmpty()
    private val earlierInputs = HashMap<DesignOccurrenceAddress, MutableList<DesignProjectionEdit>>()

    fun emit(
        node: JsonNode,
        scope: DesignProjectionScope,
        parent: DesignOccurrence?,
    ): DesignOccurrence? =
        when (node.designString("kind")) {
            "slot" -> slot(node, scope, parent)
            "repeat" -> repeat(node, scope, parent)
            else -> layer(node, scope, parent)
        }

    private fun layer(
        node: JsonNode,
        scope: DesignProjectionScope,
        parent: DesignOccurrence?,
    ): DesignOccurrence {
        val component = blocks[node.designString("component")]
        val rec = DesignOccurrence(node, scope, parent, component)
        index.getOrPut(rec.address, ::LinkedHashSet).add(rec)
        parent?.children?.add(rec)
        if (node.designString("kind") == "instance") {
            refresh(rec)
            earlierInputs[rec.address].orEmpty().forEach(edits::apply)
        } else {
            node.designChildren().forEach { emit(it, scope, rec) }
        }
        return rec
    }

    private fun slot(
        node: JsonNode,
        scope: DesignProjectionScope,
        parent: DesignOccurrence?,
    ): DesignOccurrence? {
        val supplied = scope.slots[node.designString("prop")]
        val content = supplied?.children ?: node.path("fallback")
        val owner = supplied?.let(scope::supplied) ?: scope
        content.forEach { emit(it, owner, parent) }
        return null
    }

    private fun repeat(
        node: JsonNode,
        scope: DesignProjectionScope,
        parent: DesignOccurrence?,
    ): DesignOccurrence? {
        val rows = scope.env.resolve(node.path("items")) ?: return null
        for (row in rows) {
            val repeated = scope.repeated(node, row)
            node.path("template").forEach { emit(it, repeated, parent) }
        }
        return null
    }

    fun refresh(rec: DesignOccurrence) {
        val component = rec.component ?: return
        if (!active.add(rec.source)) return
        forgetChildren(rec)
        val scope = rec.scope.mounted(rec.source, DesignBindingScope(rec.props, rec.inputs), rec.slots)
        emit(component.path("root"), scope, rec)
        component.path("variants").filter { matches(it, rec) }.forEach { variant(it, scope) }
        val prefix = rec.scope.copy(via = scope.via)
        val site = listOf(rec.scope.owner, rec.address.node, "overrides")
        rec.source.path("overrides").forEach { edits.apply(DesignProjectionEdit(it, prefix, site)) }
        active.remove(rec.source)
    }

    fun variants(
        variants: JsonNode,
        scope: DesignProjectionScope,
    ) {
        variants.forEach { variant(it, scope) }
    }

    private fun variant(
        variant: JsonNode,
        scope: DesignProjectionScope,
    ) {
        val op = JsonNodeFactory.instance.objectNode().put("op", "set")
        op.set("target", variant.path("target")).set("fields", variant.path("set"))
        edits.apply(DesignProjectionEdit(op, scope, listOf(scope.owner, "variants")))
    }

    private fun matches(
        variant: JsonNode,
        rec: DesignOccurrence,
    ): Boolean {
        val whenValue = variant.path("when")
        return rec.inputs[whenValue.designString("prop")] == whenValue.get("choice")
    }

    fun stateEdit(edit: DesignProjectionEdit) {
        edits.apply(edit)
        if (edit.change.designString("op") in DesignProjectionEdits.inputOperations) {
            earlierInputs.getOrPut(edit.target(), ::ArrayList).add(edit)
        }
    }

    private fun forgetChildren(rec: DesignOccurrence) {
        val pending = ArrayDeque(rec.children)
        rec.children.clear()
        while (pending.isNotEmpty()) {
            val child = pending.removeLast()
            index[child.address]?.remove(child)
            pending.addAll(child.children)
        }
    }

    fun find(
        key: DesignOccurrenceAddress,
        fact: DesignInputFact,
    ): DesignOccurrence? {
        val nodes = index[key].orEmpty()
        if (nodes.size > 1) rules.facts.invalid("ambiguous occurrence: ${key.owner}/${key.node}")
        if (nodes.isEmpty()) rules.facts.missing(fact, "missing occurrence: ${key.owner}/${key.node}")
        return nodes.singleOrNull()
    }

    fun resolveRadius(rec: DesignOccurrence) {
        val value = rec.patch.changed ?: rec.source
        val reference = value.path("style").path("radius").get("token") ?: return
        val mode =
            generateSequence(rec) { it.parent }.mapNotNull { it.source.get("themeMode")?.asString() }.firstOrNull()
                ?: blocks[rec.scope.owner]?.get("themeMode")?.asString()
        val resolved = radius(reference, mode)
        rec.patch.leaf("style.radius", resolved)
    }

    private fun radius(
        reference: JsonNode,
        mode: String?,
    ): JsonNode {
        var id = reference.asString("")
        val seen = HashSet<String>()
        while (seen.add(id)) {
            val token = tokens[id] ?: break
            val variant = token.path("modes").firstOrNull { it.designString("mode") == mode }
            val value = variant?.get("value") ?: token.path("value")
            if (!value.has("token")) return value
            id = value.path("token").asString("")
        }
        return JsonNodeFactory.instance.objectNode()
    }

    private fun checkValue(rec: DesignOccurrence) {
        val changed = rec.patch.changed
        DesignOccurrenceScroll.validate(changed ?: rec.source, rules.facts)
        if (changed == null || !rules.facts.checking) return
        DesignSchema.validateNode(changed)
        DesignGeometryRules.validate(changed)
        tokenRules.validateFields(changed)
    }

    fun finish(annotations: List<JsonNode>) {
        index.values
            .asSequence()
            .flatten()
            .forEach(::checkValue)
        annotations.filter { it.path("target").has("node") }.forEach { a ->
            val fact =
                rules.facts.track(
                    listOf(a.designString("id"), "annotation", a.designString("screen"), a.designString("state")),
                    a.path("target"),
                )
            find(DesignOccurrenceAddress.from(a.path("target"), emptyList()), fact)
        }
    }
}
