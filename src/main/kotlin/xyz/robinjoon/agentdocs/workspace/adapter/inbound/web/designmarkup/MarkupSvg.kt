package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.jsoup.nodes.Element

internal class MarkupSvg(
    private val styles: MarkupStyles,
) {
    fun group(e: Element): Fields {
        val numbers =
            e
                .attr("viewBox")
                .trim()
                .split(Regex("\\s+"))
                .map(::decimal)
        if (numbers.size != 4 || numbers[2].signum() <= 0 || numbers[3].signum() <= 0) invalid("Expected viewBox x y width height")
        val declarations = MarkupStyles.declarations(e.attr("style")).toMutableMap()
        listOf("width", "height").forEach { dim ->
            e.optional(dim)?.let {
                if (declarations.put(dim, "${decimal(it)}px") != null) invalid("Duplicate SVG $dim")
            }
        }
        return mapOf(
            "kind" to "group",
            "viewBox" to mapOf("x" to numbers[0], "y" to numbers[1], "width" to numbers[2], "height" to numbers[3], "fit" to "contain"),
        ) + styles.node(declarations, false) + MarkupPath.rotation(e.optional("transform"))
    }

    fun path(e: Element): Fields {
        e.only(*pathAttributes)
        if (e.childNodeSize() != 0) invalid("SVG path must be empty")
        val rule = e.optional("fill-rule") ?: "nonzero"
        if (rule !in setOf("nonzero", "evenodd")) invalid("Invalid fill-rule")
        val fill = e.optional("fill") ?: "#000000"
        val paints = if (fill == "none") emptyList() else listOf(paint(e.optional("data-fill-key") ?: "fill", fill))
        if (paints.isEmpty() && e.hasAttr("data-fill-key")) invalid("A missing fill cannot have an ID")
        return mapOf(
            "kind" to "vector",
            "fillRule" to rule,
            "contours" to listOf(MarkupPath(e.attr("d")).contour(e)),
            "style" to (mapOf("fills" to paints) + strokes(e)),
        ) + MarkupPath.rotation(e.optional("transform"))
    }

    private fun paint(
        id: String,
        value: String,
    ): Fields = mapOf("id" to identifier(id), "kind" to "solid", "color" to styles.color(value))

    private fun strokes(e: Element): Fields {
        val ink = e.optional("stroke")
        if (ink == null || ink == "none") {
            if (strokeAttributes.any(e::hasAttr)) invalid("Stroke attributes require a visible stroke")
            return emptyMap()
        }
        val stroke =
            mutableMapOf<String, Any?>(
                "id" to identifier(e.optional("data-stroke-key") ?: "stroke"),
                "width" to nonnegative(cssNumber(e.optional("stroke-width") ?: "1")),
                "paint" to paint(e.optional("data-ink-key") ?: "ink", ink),
            )
        e.optional("stroke-linecap")?.let { stroke["cap"] = it.takeIf(caps::contains) ?: invalid("Unsupported SVG stroke-linecap") }
        e.optional("stroke-linejoin")?.let { stroke["join"] = it.takeIf(joins::contains) ?: invalid("Unsupported SVG stroke-linejoin") }
        return mapOf("strokes" to listOf(stroke))
    }

    private companion object {
        val caps = setOf("butt", "round", "square")
        val joins = setOf("round", "miter", "bevel")
        val pathAttributes =
            arrayOf(
                "id",
                "data-key",
                "d",
                "fill",
                "stroke",
                "fill-rule",
                "stroke-width",
                "stroke-linecap",
                "stroke-linejoin",
                "data-contour-key",
                "data-points",
                "data-fill-key",
                "data-stroke-key",
                "data-ink-key",
                "transform",
            )
        val strokeAttributes = setOf("data-stroke-key", "data-ink-key", "stroke-width", "stroke-linecap", "stroke-linejoin")
    }
}
