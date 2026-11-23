package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignFields

/** Resolve just the local appearance that a comment refers to, including token aliases in the viewed mode. */
internal class DesignCommentValues(
    blocks: List<FlowDesignBlock>,
) {
    private val library = blocks.firstOrNull { it.kind == "tokens" }
    private val tokens =
        library
            ?.fields
            ?.get("entries")
            .designObjects()
            .associateBy { it["id"] }
    private val styles =
        library
            ?.fields
            ?.get("styles")
            .designObjects()
            .associateBy { it["id"] }

    fun resolve(
        value: Any?,
        mode: String?,
        binding: (Any?) -> Any?,
    ): Any? {
        val visiting = mutableSetOf<Any?>()

        fun resolved(item: Any?): Any? =
            when (item) {
                is List<*> -> {
                    item.map(::resolved)
                }

                is Map<*, *> -> {
                    val bound = boundValue(item, binding)
                    if (bound !== item) resolved(bound) else withReferences(item, mode, visiting, ::resolved)
                }

                else -> {
                    item
                }
            }
        return resolved(value)
    }

    private fun boundValue(
        item: DesignFields,
        binding: (Any?) -> Any?,
    ): Any? = if (item.keys == propBinding || item.keys == rowBinding) binding(item) else item

    private fun withReferences(
        item: DesignFields,
        mode: String?,
        visiting: MutableSet<Any?>,
        resolve: (Any?) -> Any?,
    ): Any? {
        val source =
            item.mapValues { (key, value) ->
                if (key == "inputs" && value is DesignFields) value.mapValues { resolve(it.value) } else resolve(value)
            }
        val token = tokens[item["token"]]
        if (token != null && visiting.add(token["id"])) {
            val chosen = token["modes"].designObjects().find { it["mode"] == mode } ?: token
            val effective = resolve(chosen["value"])
            visiting.remove(token["id"])
            return source to effective
        }
        val style = styles[item["sharedStyle"]] ?: return source
        return source + ("style" to resolve(merged(style["value"], item["style"] ?: emptyMap<String, Any>())))
    }

    companion object {
        private val propBinding = setOf("prop")
        private val rowBinding = setOf("repeat", "field")

        fun apply(
            source: DesignFields,
            change: DesignFields,
        ): DesignFields =
            when (change["op"]) {
                "set" -> requireNotNull(merged(source, change["fields"]) as? DesignFields)
                "hide" -> source + ("visible" to false)
                "leaf", "mask" -> writePath(source, change["path"].toString().split('.'), change)
                "input" -> source + ("inputs" to ((source["inputs"] as? DesignFields).orEmpty() + (change["prop"] to change["value"])))
                else -> source
            }

        private fun writePath(
            source: DesignFields,
            path: List<String>,
            change: DesignFields,
        ): DesignFields {
            val key = path.first()
            if (path.size == 1) {
                return if (change["op"] == "mask") source - key else source + (key to change["value"])
            }
            return source + (key to writePath((source[key] as? DesignFields).orEmpty(), path.drop(1), change))
        }

        private fun merged(
            base: Any?,
            patch: Any?,
        ): Any? {
            if (base !is DesignFields || patch !is DesignFields) return patch
            if ("token" in patch || "prop" in patch || "repeat" in patch) return patch
            val fields = base.entries.associate { it.key to it.value }.toMutableMap()
            patch.forEach { (k, v) -> fields[k] = merged(base[k], v) }
            return fields
        }
    }
}
