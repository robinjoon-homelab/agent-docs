package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

internal data class MarkupResources(
    val colors: Set<String>,
    val styles: Set<String>,
    val components: Map<String, Map<String, Fields>>,
)

internal class MarkupLayers(
    private val resources: MarkupResources,
    private val props: Set<String>,
    root: Element,
    existing: Set<String> = emptySet(),
) {
    private val ids = MarkupIds(root.allElements.mapNotNull { it.optional("data-key") }, existing)
    private val styles = MarkupStyles(resources.colors)
    private val svg = MarkupSvg(styles)

    fun build(e: Element): Fields = e.located { common(e) + specific(e) }

    private fun specific(e: Element): Fields =
        when (e.tagName()) {
            "div", "section", "button" -> {
                box(e)
            }

            "p", "span" -> {
                e.only(*common, "data-bind")
                mapOf("kind" to "text", "text" to text(e)) + css(e, false)
            }

            "flow-use" -> {
                instance(e)
            }

            "svg" -> {
                e.only(*common, "viewBox", "width", "height", "transform")
                val paths = e.layers()
                if (paths.any { it.tagName() != "path" }) invalid("Only path children are allowed in SVG")
                svg.group(e) + ("children" to paths.map { p -> p.located { common(p) + svg.path(p) } })
            }

            else -> {
                invalid("Unsupported element: " + e.tagName())
            }
        }

    private fun common(e: Element): Fields =
        buildMap {
            put("id", ids.allocate(e.optional("data-key"), "n"))
            e.optional("id")?.let { put("alias", identifier(it)) }
            e.optional("role")?.let { put("role", it) }
            e.optional("aria-label")?.let { put("accessibleName", it) }
            e.optional("class")?.let {
                if (it !in resources.styles) invalid("Unknown shared style: $it")
                put("sharedStyle", it)
            }
        }

    private fun css(
        e: Element,
        box: Boolean,
    ): Fields = styles.node(MarkupStyles.declarations(e.attr("style")), box)

    private fun box(e: Element): Fields {
        val fields = mutableMapOf<String, Any?>("kind" to "box")
        if (e.tagName() == "button") {
            e.only(*common, "type")
            if (e.hasAttr("type") && e.attr("type") != "button") invalid("Expected type=button")
            fields["role"] = e.optional("role") ?: "button"
        } else {
            e.only(*common)
        }
        return fields + css(e, true) + ("children" to e.layers().map(::build))
    }

    private fun instance(e: Element): Fields {
        val component = e.attr("component")
        val definition = resources.components[component] ?: invalid("Unknown component: $component")
        val inputs = e.attributes().filter { it.key.startsWith("data-input-") }
        e.only(*common, "component", *inputs.map { it.key }.toTypedArray())
        if (e.childNodeSize() > 0) invalid("Component instances must be empty")
        val values =
            inputs.associate {
                val id = it.key.removePrefix("data-input-")
                id to input(it.value, definition[id] ?: invalid("Unknown component property: $id"))
            }
        val fields = mutableMapOf<String, Any?>("kind" to "instance", "component" to component)
        if (values.isNotEmpty()) fields["inputs"] = values
        return fields + css(e, false)
    }

    private fun input(
        raw: String,
        definition: Fields,
    ): Any =
        when (definition["type"]) {
            "text" -> {
                raw
            }

            "number" -> {
                decimal(raw)
            }

            "boolean" -> {
                raw.toBooleanStrictOrNull() ?: invalid("Expected true or false")
            }

            "choice" -> {
                if (objects(definition["choices"]).none { it["id"] == raw }) invalid("Unknown choice: $raw")
                raw
            }

            else -> {
                invalid("Unsupported markup input type: " + definition["type"])
            }
        }

    private fun bound(e: Element): Any {
        val binding = e.optional("data-bind") ?: return e.textOnly()
        if (e.childNodeSize() != 0 || binding !in props) invalid("Unknown or nonempty text binding: $binding")
        return mapOf("prop" to binding)
    }

    private fun text(e: Element): Any {
        if (e.hasAttr("data-bind") || e.childNodes().all { it is TextNode }) return bound(e)
        val runIds = MarkupIds(e.children().mapNotNull { it.optional("data-run-key") })
        return mapOf(
            "runs" to
                e.childNodes().map {
                    when (it) {
                        is TextNode -> mapOf("id" to runIds.allocate(null, "r"), "text" to it.wholeText)
                        is Element -> it.located { run(it, runIds) }
                        else -> invalid("Unexpected inline content")
                    }
                },
        )
    }

    private fun run(
        e: Element,
        runIds: MarkupIds,
    ): Fields {
        e.only("data-run-key", "style", "data-bind")
        val rules = MarkupStyles.declarations(e.attr("style")).toMutableMap()
        when (e.tagName()) {
            "strong" -> rules.putIfAbsent("font-weight", "700")
            "em" -> rules.putIfAbsent("font-style", "italic")
            "del" -> rules.putIfAbsent("text-decoration-line", "line-through")
            "span" -> Unit
            else -> invalid("Unsupported inline element: " + e.tagName())
        }
        val style = styles.surface(rules)
        if ("fill" in style) invalid("A text run cannot have a background fill")
        val fields = mutableMapOf<String, Any?>("id" to runIds.allocate(e.optional("data-run-key"), "r"), "text" to bound(e))
        style["text"]?.let { fields["style"] = it }
        return fields
    }

    private companion object {
        val common = arrayOf("id", "data-key", "role", "aria-label", "class", "style")
    }
}
