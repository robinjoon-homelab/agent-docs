package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

internal class MarkupStyles(
    private val colorTokens: Set<String>,
) {
    fun color(value: String): Any {
        if (hex.matches(value) || value == "currentColor") return value
        val id = token.matchEntire(value)?.groupValues?.get(1) ?: invalid("Unsupported color: $value")
        if (id !in colorTokens) invalid("Unknown color token: $id")
        return mapOf("token" to id)
    }

    fun surface(input: Map<String, String>): Fields {
        val text = mutableMapOf<String, Any?>()
        val surface = mutableMapOf<String, Any?>()
        input.forEach { (key, value) ->
            if (key == "background-color") {
                surface["fill"] = color(value)
            } else {
                text.putAll(textProperty(key, value))
            }
        }
        if (text.isNotEmpty()) surface["text"] = text
        return surface
    }

    private fun textProperty(
        key: String,
        value: String,
    ): Fields =
        when (key) {
            "color" -> {
                mapOf("color" to color(value))
            }

            "font-size" -> {
                mapOf("size" to (px(value).takeIf { it.signum() > 0 } ?: invalid("Font size must be positive")))
            }

            "font-weight" -> {
                val weight = cssNumber(value)
                if (weight < 1.toBigDecimal() || weight > 1000.toBigDecimal()) invalid("Expected font weight 1..1000")
                mapOf("weight" to weight)
            }

            "font-style" -> {
                mapOf("italic" to (choice(value, setOf("normal", "italic")) == "italic"))
            }

            "text-decoration-line" -> {
                decoration(value)
            }

            else -> {
                invalid("Unsupported CSS property: $key")
            }
        }

    fun node(
        input: Map<String, String>,
        box: Boolean,
    ): Fields {
        val result = mutableMapOf<String, Any?>()
        if (!box && input.keys.any { it in layoutKeys }) invalid("Only containers accept layout declarations")
        if (box) result["layout"] = layout(input)
        val dimensions =
            listOf("width" to "x", "height" to "y")
                .mapNotNull { (key, axis) ->
                    input[key]?.let { axis to mapOf("mode" to "fixed", "px" to px(it)) }
                }.toMap()
        if (dimensions.isNotEmpty()) result["size"] = dimensions
        input["box-sizing"]?.let { result["boxSizing"] = choice(it, setOf("border-box", "content-box")) }
        input["grid-column"]?.let { result["cell"] = mapOf("column" to column(it)) }
        val item = flexItem(input)
        if (item.isNotEmpty()) result["flexItem"] = item
        val paint = surface(input - (layoutKeys + commonKeys))
        if (paint.isNotEmpty()) result["style"] = paint
        return result
    }

    companion object {
        val hex = Regex("#(?:[a-fA-F0-9]{3,4}|[a-fA-F0-9]{6}|[a-fA-F0-9]{8})")
        private val token = Regex("var\\(--([A-Za-z_][A-Za-z0-9_-]*)\\)")
        private val layoutKeys = setOf("display", "flex-direction", "gap", "padding", "grid-template-columns")
        private val commonKeys = setOf("width", "height", "box-sizing", "grid-column", "flex", "flex-grow", "flex-shrink", "flex-basis")

        fun declarations(source: String): Map<String, String> {
            val pairs =
                source.trim().removeSuffix(";").split(';').filter { it.isNotBlank() }.map { declaration ->
                    val pair = declaration.split(':', limit = 2)
                    if (pair.size != 2 || pair[1].isBlank()) invalid("Invalid CSS declaration: $declaration")
                    pair[0].trim() to pair[1].trim()
                }
            if (pairs.distinctBy { it.first }.size != pairs.size) invalid("Duplicate CSS declaration")
            return pairs.toMap()
        }

        private fun px(value: String): java.math.BigDecimal {
            if (value != "0" && !value.endsWith("px")) invalid("Expected px: $value")
            return nonnegative(cssNumber(value.removeSuffix("px")))
        }

        private fun choice(
            value: String,
            options: Set<String>,
        ): String = value.takeIf { it in options } ?: invalid("Unsupported CSS value: $value")

        private fun decoration(value: String): Fields =
            when (value) {
                "underline" -> mapOf("underline" to true)
                "line-through" -> mapOf("strike" to true)
                "none" -> mapOf("strike" to false, "underline" to false)
                else -> invalid("Unsupported text-decoration-line: $value")
            }

        private fun layout(input: Map<String, String>): Fields {
            val result = mode(input).toMutableMap()
            input["gap"]?.let { result["gap"] = px(it) }
            input["padding"]?.let { result["padding"] = listOf("left", "top", "right", "bottom").associateWith { _ -> px(it) } }
            return result
        }

        private fun mode(input: Map<String, String>): Fields {
            val display = input["display"]
            if (display == "grid") {
                if (input.containsKey("flex-direction")) invalid("Conflicting grid and flex declarations")
                return mapOf("mode" to "grid", "columns" to tracks(input["grid-template-columns"] ?: invalid("Grid columns required")))
            }
            if ((display != null && display != "flex") || input.containsKey("grid-template-columns")) invalid("Unsupported layout")
            if (display == null && input.containsKey("flex-direction")) invalid("flex-direction requires display:flex")
            val direction = input["flex-direction"] ?: if (display == "flex") "row" else "column"
            return mapOf("mode" to choice(direction, setOf("row", "column")))
        }

        private val flexTrack = Regex("minmax\\(0,([0-9.]+)fr\\)")

        private fun tracks(value: String): List<Any> =
            value.replace(Regex(",\\s+"), ",").split(Regex("\\s+")).map { track ->
                val fr = flexTrack.matchEntire(track)?.groupValues?.get(1)
                if (fr ==
                    null
                ) {
                    px(track)
                } else {
                    mapOf(
                        "min" to 0,
                        "fr" to (cssNumber(fr).takeIf { it.signum() > 0 } ?: invalid("fr must be positive")),
                    )
                }
            }

        private fun column(value: String): Any {
            if (value.replace(" ", "") == "1/-1") return "all"
            val span =
                Regex("span\\s+(\\d+)")
                    .matchEntire(value)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: invalid("Unsupported grid-column")
            if (span < 1) invalid("grid-column span must be positive")
            return mapOf("span" to span)
        }

        private fun basis(value: String): Any =
            when {
                value == "auto" -> "auto"
                value.endsWith('%') -> mapOf("percent" to nonnegative(cssNumber(value.dropLast(1))))
                else -> px(value)
            }

        private fun flexItem(input: Map<String, String>): Fields {
            val shorthand = input["flex"]
            val parts = listOf("flex-grow", "flex-shrink", "flex-basis")
            if (shorthand == null) {
                return parts
                    .mapNotNull { k ->
                        input[k]?.let { k.removePrefix("flex-") to if (k == "flex-basis") basis(it) else nonnegative(cssNumber(it)) }
                    }.toMap()
            }
            if (parts.any(input::containsKey)) invalid("Duplicate flex declarations")
            val split = shorthand.split(Regex("\\s+"))
            if (split.size == 1) return mapOf("grow" to nonnegative(cssNumber(shorthand)), "shrink" to 1, "basis" to mapOf("percent" to 0))
            if (split.size != 3) invalid("Expected grow shrink basis")
            return mapOf(
                "grow" to nonnegative(cssNumber(split[0])),
                "shrink" to nonnegative(cssNumber(split[1])),
                "basis" to basis(split[2]),
            )
        }
    }
}
