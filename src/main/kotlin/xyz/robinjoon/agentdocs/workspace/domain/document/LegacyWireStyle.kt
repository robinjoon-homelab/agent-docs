package xyz.robinjoon.agentdocs.workspace.domain.document

internal object LegacyWireStyle {
    const val TEXT = "#1f1f1d"
    const val MUTED = "#5f5e5a"
    const val SOFT = "#6b6a65"
    const val SURFACE = "#ffffff"
    const val SUNKEN = "#f7f7f5"
    const val BORDER = "#e3e2de"
    const val STRONG = "#cfcdc7"

    fun border(
        color: String,
        width: Number = 1,
        line: String = "solid",
    ): WireFields {
        val edge = mapOf("width" to width, "color" to color, "line" to line)
        return listOf("top", "right", "bottom", "left").associateWith { edge }
    }

    fun radius(value: Int): WireFields = listOf("topLeft", "topRight", "bottomLeft", "bottomRight").associateWith { value }

    fun font(
        size: Number,
        weight: Int = 400,
        color: String = TEXT,
    ): WireFields = mapOf("size" to size, "weight" to weight, "color" to color)

    fun face(
        fill: String,
        radius: Int,
        border: String? = null,
    ): WireFields =
        mapOf("fill" to fill, "radius" to radius(radius)) + if (border == null) emptyMap() else mapOf("border" to border(border))

    fun padding(
        x: Int,
        y: Int = 0,
    ): WireFields = mapOf("left" to x, "right" to x, "top" to y, "bottom" to y)

    fun box(
        id: String,
        layout: WireFields,
        children: List<WireFields>,
    ): WireFields = mapOf("kind" to "box", "id" to id, "layout" to layout, "children" to children)

    fun bind(prop: String): WireFields = mapOf("prop" to prop)

    fun text(
        id: String,
        content: Any,
        font: WireFields,
    ): WireFields = mapOf("kind" to "text", "id" to id, "text" to content, "style" to mapOf("text" to font))

    fun icon(
        name: String,
        px: Int,
    ): WireFields =
        mapOf(
            "kind" to "icon",
            "id" to name,
            "icon" to mapOf("builtin" to name),
            "size" to mapOf("x" to mapOf("mode" to "fixed", "px" to px), "y" to mapOf("mode" to "fixed", "px" to px)),
        )

    fun row(
        x: Int = 0,
        justify: String = "start",
    ): WireFields = mapOf("mode" to "row", "align" to "center", "justify" to justify, "padding" to padding(x))
}
