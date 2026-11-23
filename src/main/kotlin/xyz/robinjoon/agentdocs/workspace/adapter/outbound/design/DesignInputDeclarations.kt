package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode

internal class DesignInputDeclarations(
    private val blocks: Map<String, JsonNode>,
    private val rules: DesignInputRules,
) {
    fun check(block: JsonNode) {
        val site = listOf(block.designString("id"))
        val scope = DesignBindingScope(props = block.designProps())
        block.get("root")?.let { read(it, scope, site) }
        block.path("changes").forEach { readChanges(it, scope, site) }
        block.path("variants").forEach { variant(it, scope, site) }
    }

    private fun readChanges(
        op: JsonNode,
        env: DesignBindingScope,
        site: List<String>,
    ) {
        op.get("node")?.let { read(it, env, site) }
        op.path("assignment").path("children").forEach { read(it, env, site) }
    }

    private fun variant(
        change: JsonNode,
        env: DesignBindingScope,
        site: List<String>,
    ) {
        val test = change.path("when")
        val prop = env.props[test.designString("prop")]
        val fact = rules.facts.track(site + "variant", test)
        if (prop == null) {
            rules.facts.missing(fact, "undeclared variant property")
            return
        }
        if (prop.designString("type") != "choice") rules.facts.invalid("variant condition requires a choice")
        rules.input(prop, test.path("choice"), env, fact)
    }

    private fun read(
        node: JsonNode,
        scope: DesignBindingScope,
        block: List<String>,
    ) {
        val site = block + node.designString("id")
        rules.visual(node, scope, site)
        when (node.designString("kind")) {
            "instance" -> instance(node, scope, site)
            "slot" -> slot(scope.props[node.designString("prop")], rules.facts.track(site, node.path("prop")))
        }
        val childScope = if (node.designString("kind") == "repeat") repeat(node, scope, site) else scope
        node.designChildren().forEach { read(it, childScope, block) }
    }

    private fun repeat(
        node: JsonNode,
        scope: DesignBindingScope,
        site: List<String>,
    ): DesignBindingScope {
        val binding = node.path("items")
        rules.binding(binding, "list", scope, rules.facts.track(site + "items", binding))
        return scope.repeated(node)
    }

    private fun instance(
        node: JsonNode,
        scope: DesignBindingScope,
        site: List<String>,
    ) {
        val component = blocks[node.designString("component")]
        if (component != null) assignments(node, component.designProps(), scope, site)
        node
            .path("slots")
            .values()
            .flatMap { it.values() }
            .forEach { read(it, scope, listOf(site.first())) }
        node.path("overrides").forEach { readChanges(it, scope, listOf(site.first())) }
    }

    private fun assignments(
        node: JsonNode,
        props: Map<String, JsonNode>,
        env: DesignBindingScope,
        site: List<String>,
    ) {
        node.path("inputs").properties().forEach { (id, value) ->
            rules.field(props[id], value, env, rules.facts.track(site + listOf("input", id), value))
        }
        node.path("slots").properties().forEach { (id, _) ->
            slot(props[id], rules.facts.track(site + listOf("slot", id), node.path("component")))
        }
    }

    private fun slot(
        property: JsonNode?,
        fact: DesignInputFact,
    ) {
        if (property == null) rules.facts.missing(fact, "undeclared slot")
        if (property != null && property.designString("type") != "slot") rules.facts.invalid("slot assignment requires a slot property")
    }
}
