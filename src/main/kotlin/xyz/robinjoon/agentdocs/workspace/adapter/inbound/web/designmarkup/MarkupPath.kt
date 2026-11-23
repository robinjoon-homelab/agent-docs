package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.jsoup.nodes.Element
import java.math.BigDecimal
import kotlin.math.cos
import kotlin.math.sin

/** Preserve editable vertices and relative Bézier handles instead of storing the SVG source. */
internal class MarkupPath(
    source: String,
) {
    private val words = pathTokens(source)
    private var offset = 0
    private var command = ""
    private val points = mutableListOf<Point>()
    private var closed = false

    fun contour(element: Element): Fields {
        while (offset < words.size) consume()
        if (points.isEmpty()) invalid("Empty SVG path")
        val ids = element.optional("data-points")?.split(Regex("\\s+"))?.map(::identifier) ?: points.indices.map { "v${it + 1}" }
        if (ids.size != points.size || ids.distinct().size != ids.size) invalid("Invalid or duplicate SVG vertex IDs")
        return mapOf(
            "id" to identifier(element.optional("data-contour-key") ?: "contour"),
            "closed" to closed,
            "vertices" to points.mapIndexed { index, point -> point.fields(ids[index]) },
        )
    }

    private fun consume() {
        if (words[offset] in commands) command = words[offset++]
        if ((points.isEmpty() && command != "M") || (points.isNotEmpty() && command == "M")) invalid("Expected one M contour per SVG path")
        when (command) {
            "M", "L" -> {
                points.add(point())
                command = "L"
            }

            "C" -> {
                curve()
            }

            "Z" -> {
                if (offset != words.size) invalid("Expected end of path after Z")
                closed = true
            }

            else -> {
                invalid("Expected an absolute SVG M, L, C or Z command")
            }
        }
    }

    private fun curve() {
        val previous = points.last()
        val first = point()
        val second = point()
        val end = point()
        previous.outgoing = first.relativeTo(previous)
        end.incoming = second.relativeTo(end)
        points.add(end)
    }

    private fun point(): Point = Point(coordinate(), coordinate())

    private fun coordinate(): BigDecimal = decimal(words.getOrNull(offset++) ?: invalid("Missing SVG path coordinate"))

    private class Point(
        val x: BigDecimal,
        val y: BigDecimal,
    ) {
        var incoming: Fields? = null
        var outgoing: Fields? = null

        fun relativeTo(other: Point): Fields = mapOf("x" to x - other.x, "y" to y - other.y)

        fun fields(id: String): Fields =
            mapOf("id" to id, "x" to x, "y" to y) + listOfNotNull(incoming?.let { "in" to it }, outgoing?.let { "out" to it })
    }

    companion object {
        private val commands = setOf("M", "L", "C", "Z")
        private val pattern = Regex("""[MLCZ]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?|[\s,]+""")

        private fun pathTokens(source: String): List<String> {
            var cursor = 0
            val result = mutableListOf<String>()
            while (cursor < source.length) {
                val match = pattern.matchAt(source, cursor) ?: invalid("Unsupported SVG path at offset $cursor")
                cursor = match.range.last + 1
                if (!match.value.first().isWhitespace() && match.value.first() != ',') result.add(match.value)
            }
            return result
        }

        fun rotation(raw: String?): Fields {
            if (raw == null) return emptyMap()
            val degrees =
                Regex("""rotate\(\s*(-?[\d.]+)\s*\)""").matchEntire(raw)?.groupValues?.get(1)
                    ?: invalid("Only rotate(degrees) is supported")
            val theta = Math.toRadians(cssNumber(degrees).toDouble())
            if (!theta.isFinite()) invalid("Invalid rotation")
            return mapOf("localTransform" to mapOf("matrix" to listOf(cos(theta), sin(theta), -sin(theta), cos(theta), 0, 0)))
        }
    }
}
