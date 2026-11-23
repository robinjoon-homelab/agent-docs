package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.BORDER
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.MUTED
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.SOFT
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.STRONG
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.SUNKEN
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.SURFACE
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.TEXT
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.bind
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.box
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.face
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.font
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.icon
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.padding
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.row
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.text

internal object LegacyWireContent {
    private val value = bind("value")

    fun root(kind: String): WireFields =
        when (kind) {
            "field", "select", "area" -> control(kind)
            "list" -> list()
            "path" -> path()
            "image" -> image()
            "box" -> box("body", mapOf("mode" to "free"), emptyList()) + ("style" to face(SURFACE, 10, BORDER))
            "bar" -> bar()
            "button" -> box("body", row(justify = "center"), listOf(caption(font(14, 600, SURFACE)))) + ("style" to face(TEXT, 8))
            "option" -> box("body", row(12), listOf(caption(font(14, color = MUTED)))) + ("style" to face(SURFACE, 8, BORDER))
            "notice" -> notice()
            else -> plain(kind)
        } + ("size" to LegacyWireComponents.fill)

    private fun bar(): WireFields {
        val style = mapOf("fill" to SURFACE, "border" to LegacyWireStyle.border(BORDER).filterKeys { it == "bottom" })
        return box("body", row(12) + ("gap" to 10), listOf(icon("menu", 18), icon("search", 18))) + ("style" to style)
    }

    private fun notice(): WireFields {
        val label = caption(font(12, color = "#1f3f8f") + ("lineHeight" to mapOf("multiple" to 1.55)))
        val body = box("body", mapOf("mode" to "column", "padding" to padding(11, 9)), listOf(label))
        return body + ("style" to face("#eef3fe", 6, "#cddcfb"))
    }

    private fun caption(style: WireFields): WireFields = text("caption", value, style)

    private fun plain(kind: String): WireFields {
        val style =
            when (kind) {
                "title" -> font(22, 700) + ("lineHeight" to mapOf("multiple" to 1.3))
                "link" -> font(14, color = "#2a5bd7") + ("underline" to true)
                "block" -> font(14) + ("lineHeight" to mapOf("multiple" to 1.65))
                "text" -> font(12, color = MUTED) + ("lineHeight" to mapOf("multiple" to 1.55))
                else -> font(12, color = MUTED)
            }
        val inset =
            when (kind) {
                "block" -> padding(2, 4)
                "crumb" -> padding(0, 1)
                "link", "text" -> padding(0, 2)
                else -> padding(0)
            }
        return box("body", mapOf("mode" to "column", "padding" to inset), listOf(caption(style)))
    }

    private fun control(kind: String): WireFields {
        val style = if (kind == "area") font(13) + ("lineHeight" to mapOf("multiple" to 1.5)) else font(13)
        val inner = mutableListOf(caption(style))
        if (kind == "select") inner.add(icon("chevron-down", 14))
        val layout = if (kind == "area") mapOf("mode" to "column", "padding" to padding(10, 8)) else row(10, "between")
        val input =
            box("input", layout, inner) +
                mapOf(
                    "style" to face(SURFACE, 6, STRONG),
                    "size" to mapOf("y" to mapOf("mode" to "fill", "min" to 0)),
                    "flexItem" to mapOf("grow" to 1, "shrink" to 1, "basis" to mapOf("percent" to 0)),
                )
        val label = text("label", bind("label"), font(12, 500) + ("lineHeight" to 16))
        return box("body", mapOf("mode" to "column", "gap" to 4), listOf(label, input))
    }

    private fun list(): WireFields {
        val content = mapOf("repeat" to "entries", "field" to "text")
        val row =
            box("row", row(12), listOf(text("caption", content, font(14)))) +
                mapOf(
                    "size" to mapOf("y" to mapOf("mode" to "fill", "min" to 0)),
                    "flexItem" to mapOf("grow" to 1, "basis" to mapOf("percent" to 0)),
                    "style" to mapOf("border" to LegacyWireStyle.border("#efefec").filterKeys { it == "top" }),
                )
        val repeat = mapOf("kind" to "repeat", "id" to "entries", "items" to bind("items"), "template" to listOf(row))
        // Clip the first separator, as the old list only placed a border between adjacent rows.
        val inset = mapOf("start" to 0, "end" to 0)
        val rows =
            box("rows", mapOf("mode" to "column"), listOf(repeat)) +
                ("position" to mapOf("mode" to "absolute", "x" to inset, "y" to inset + ("start" to -1)))
        return box("body", mapOf("mode" to "free"), listOf(rows)) + ("style" to (face(SURFACE, 8, BORDER) + ("overflow" to "clip")))
    }

    private fun path(): WireFields {
        val dateRuns = listOf(mapOf("id" to "prefix", "text" to "오늘: "), mapOf("id" to "value", "text" to bind("date")))
        val date = text("date", mapOf("runs" to dateRuns), font(11.5)) + ("visible" to false)
        val children = listOf(date, pathLine("plainPath", bind("label")), pathLine("datedPath", "저장 경로") + ("visible" to false))
        return box("body", mapOf("mode" to "column", "padding" to padding(10, 5)), children) + ("style" to face(SUNKEN, 6))
    }

    private fun pathLine(
        id: String,
        prefix: Any,
    ): WireFields {
        val runs =
            listOf(
                mapOf("id" to "prefix", "text" to prefix),
                mapOf("id" to "colon", "text" to ": "),
                mapOf("id" to "value", "text" to value, "style" to mapOf("family" to "mono", "weight" to 500, "wordBreak" to "break-all")),
            )
        return text(id, mapOf("runs" to runs), font(11.5) + ("lineHeight" to mapOf("multiple" to 1.6)))
    }

    private fun image(): WireFields {
        val body = listOf(icon("image", 22), caption(font(12, color = SOFT)))
        val placeholder =
            box("placeholder", mapOf("mode" to "column", "align" to "center", "justify" to "center", "gap" to 6), body) +
                mapOf("style" to face("#efefec", 8), "size" to LegacyWireComponents.fill)
        val slot = mapOf("kind" to "slot", "id" to "pictureSlot", "prop" to "picture", "fallback" to listOf(placeholder))
        return box("body", mapOf("mode" to "column"), listOf(slot))
    }
}
