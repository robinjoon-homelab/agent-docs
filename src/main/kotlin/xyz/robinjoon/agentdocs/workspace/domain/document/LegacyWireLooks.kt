package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.BORDER
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.SOFT
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.STRONG
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.SURFACE
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.TEXT
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.border
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.face
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyWireStyle.radius

internal object LegacyWireLooks {
    val choices: Map<String, List<String>> =
        mapOf(
            "title" to listOf("large", "small"),
            "button" to listOf("primary", "secondary", "disabled"),
            "notice" to listOf("info", "error", "ok"),
            "box" to listOf("white", "sheet", "gray", "dim", "outline", "handle"),
        ) + listOf("area", "field", "select").associateWith { listOf("normal", "inactive") }

    fun variants(kind: String): List<WireFields> {
        if (kind == "path") {
            return listOf("plainPath" to false, "datedPath" to true, "date" to true).map { (node, shown) ->
                rule(kind, node, "dated", mapOf("visible" to shown))
            }
        }
        return choices[kind].orEmpty().flatMap { choice ->
            skins(kind, choice).map { (node, style) -> rule(kind, node, choice, mapOf("style" to style)) }
        }
    }

    private fun rule(
        kind: String,
        node: String,
        choice: String,
        updates: WireFields,
    ): WireFields =
        mapOf(
            "when" to mapOf("prop" to if (kind == "path") "dateLayout" else "look", "choice" to choice),
            "target" to mapOf("owner" to LegacyWireComponents.key(kind), "node" to node),
            "set" to updates,
        )

    private fun skins(
        kind: String,
        look: String,
    ): Map<String, WireFields> =
        when {
            look == "small" -> mapOf("caption" to mapOf("text" to mapOf("size" to 15)))
            look == "secondary" -> surfaceAndInk("body", face(SURFACE, 8, STRONG), TEXT)
            look == "disabled" -> surfaceAndInk("body", face("#e7e7e3", 8), SOFT)
            look == "inactive" -> surfaceAndInk("input", face(LegacyWireStyle.SUNKEN, 6, STRONG), SOFT)
            look == "error" -> surfaceAndInk("body", face("#fdece8", 6, "#f5c9bf"), "#a42d18")
            look == "ok" -> surfaceAndInk("body", face("#e3f5e8", 6, "#bfe5cb"), "#1d7a3d")
            kind == "box" && look != "white" -> mapOf("body" to box(look))
            else -> emptyMap()
        }

    private fun surfaceAndInk(
        node: String,
        surface: WireFields,
        color: String,
    ): Map<String, WireFields> = mapOf(node to surface, "caption" to mapOf("text" to mapOf("color" to color)))

    private fun box(look: String): WireFields {
        val plainBorder = mapOf("border" to border(BORDER, 0))
        return when (look) {
            "sheet" -> plainBorder + mapOf("radius" to mapOf("topLeft" to 18, "topRight" to 18, "bottomLeft" to 0, "bottomRight" to 0))
            "gray" -> plainBorder + face("#efefec", 6)
            "dim" -> plainBorder + face("#1414126b", 0)
            "outline" -> mapOf("fill" to "#00000000", "border" to border("#9a9893", 1.5, "dashed"), "radius" to radius(8))
            else -> plainBorder + face(STRONG, 2)
        }
    }
}
