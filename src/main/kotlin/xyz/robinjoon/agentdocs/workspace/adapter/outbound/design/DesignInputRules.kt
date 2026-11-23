package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode

internal class DesignInputRules(
    val facts: DesignInputFacts,
) {
    fun binding(
        source: JsonNode,
        expected: String,
        env: DesignBindingScope,
        fact: DesignInputFact,
    ): JsonNode? {
        val declaration = env.declaration(source)
        if (declaration == null) {
            facts.missing(fact, "undeclared binding at ${fact.site.joinToString("/")}")
            return null
        }
        val actual = declaration.designString("type")
        if (actual != expected && !(expected == "text" && actual == "choice")) {
            facts.invalid("binding of type $actual cannot supply $expected")
        }
        return declaration
    }

    fun input(
        prop: JsonNode,
        value: JsonNode,
        env: DesignBindingScope,
        fact: DesignInputFact,
    ) {
        val type = prop.designString("type")
        if (value.isDesignBinding()) {
            checkBoundInput(prop, value, env, fact)
            return
        }
        when (type) {
            "choice" -> choice(prop, value, fact)
            "list" -> list(prop, value, env, fact)
            "slot" -> facts.invalid("slot content must be supplied through a slot")
            else -> if (!isValue(type, value)) facts.invalid("expected $type for ${prop.designString("id")}")
        }
    }

    fun visual(
        node: JsonNode,
        env: DesignBindingScope,
        site: List<String>,
    ) {
        visualFields.forEach { key ->
            node.get(key)?.let { walk(it, env, site + key) }
        }
    }

    private fun walk(
        value: JsonNode,
        env: DesignBindingScope,
        path: List<String>,
    ) {
        when {
            value.isDesignBinding() -> {
                binding(value, expected(path.last()), env, facts.track(path, value))
            }

            value.isArray -> {
                value.forEachIndexed {
                    index,
                    item,
                    ->
                    walk(item, env, path + (item.get("id")?.asString() ?: index.toString()))
                }
            }

            value.isObject -> {
                value.properties().forEach { (k, v) -> walk(v, env, path + k) }
            }
        }
    }

    private fun checkBoundInput(
        prop: JsonNode,
        value: JsonNode,
        env: DesignBindingScope,
        fact: DesignInputFact,
    ) {
        val type = prop.designString("type")
        val from = binding(value, type, env, fact) ?: return
        if (type != "choice" || from.designString("type") != "choice") return
        val allowed =
            prop
                .path("choices")
                .values()
                .map { it.designString("id") }
                .toSet()
        if (from.path("choices").any { it.designString("id") !in allowed }) facts.invalid("incompatible choice binding")
    }

    private fun choice(
        prop: JsonNode,
        value: JsonNode,
        fact: DesignInputFact,
    ) {
        if (prop.path("choices").none { it.get("id") == value }) facts.missing(fact, "undeclared choice for ${prop.designString("id")}")
    }

    private fun list(
        prop: JsonNode,
        value: JsonNode,
        env: DesignBindingScope,
        fact: DesignInputFact,
    ) {
        if (!value.isArray) {
            facts.invalid("expected list input")
            return
        }
        val keys = HashSet<String>()
        val fields = prop.path("fields").associateBy { it.designString("id") }
        value.forEach { row ->
            if (!keys.add(row.designString("itemKey"))) facts.invalid("duplicate list item key")
            row.path("fields").properties().forEach { (key, item) -> field(fields[key], item, env, fact) }
        }
    }

    fun field(
        prop: JsonNode?,
        value: JsonNode,
        env: DesignBindingScope,
        fact: DesignInputFact,
    ) {
        if (prop == null) facts.missing(fact, "undeclared input or list field") else input(prop, value, env, fact)
    }

    private fun expected(field: String): String =
        when (field) {
            "visible" -> "boolean"
            "asset", "svgAsset", "image" -> "asset"
            "color", "fill", "ink" -> "color"
            "text", "alt", "builtin", "accessibleName" -> "text"
            else -> "number"
        }

    private fun isValue(
        type: String,
        value: JsonNode,
    ): Boolean =
        when (type) {
            "text" -> value.isString
            "asset" -> value.isString && assetId.matches(value.stringValue())
            "boolean" -> value.isBoolean
            "number" -> value.isNumber
            "color" -> validColor(value)
            else -> false
        }

    private fun validColor(value: JsonNode): Boolean {
        if (value.isObject) return value.has("space") || value.has("token")
        val color = value.asString("")
        return color == "currentColor" || hexColor.matches(color)
    }

    private companion object {
        val assetId = Regex("[A-Za-z0-9_-]+")
        val hexColor = Regex("#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
        val visualFields =
            listOf("size", "position", "layout", "margin", "cell", "style", "ink", "text", "visible", "asset", "alt", "icon", "flexItem")
    }
}
