package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.jsoup.nodes.Element
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonReader
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonWriter

internal fun objects(value: Any?): List<Fields> =
    (value as? List<*>).orEmpty().map { item ->
        val fields = item as? Map<*, *> ?: invalid("Expected a design object")
        fields.entries.associate { it.key.toString() to it.value }
    }

internal fun Fields.key(): String = get("id") as? String ?: invalid("Missing model ID")

internal fun merge(
    base: List<Fields>,
    additions: List<Fields>,
): List<Fields> {
    val values = base.associateBy { it.key() }.toMutableMap()
    val seen = mutableSetOf<String>()
    for (item in additions) {
        val id = item.key()
        if (!seen.add(id)) invalid("Duplicate definition: $id")
        val previous = values.put(id, item)
        if (previous != null &&
            previous != DesignJsonReader(DesignJsonWriter.write(item)).read()
        ) {
            invalid("Existing definition differs: $id")
        }
    }
    return values.values.toList()
}

internal class MarkupLibrary(
    existing: Fields?,
    sheets: List<Element>,
) {
    private val tokenRules = mutableListOf<Fields>()

    private data class SurfaceRule(
        val id: String,
        val declarations: Map<String, String>,
        val sheet: Element,
    )

    private val styleRules = mutableListOf<SurfaceRule>()
    private val rule = Regex("""\s*(:root|\.[A-Za-z_][A-Za-z_0-9-]*)\s*\{([^{}]*)}""")
    val entries: List<Fields>
    val styles: List<Fields>
    val colors: Set<String>
    val changed: Boolean get() = tokenRules.isNotEmpty() || styleRules.isNotEmpty()

    init {
        sheets.forEach { sheet -> sheet.located { readSheet(sheet) } }
        entries = merge(objects(existing?.get("entries")), tokenRules)
        colors = entries.filter { it["type"] == "color" }.mapTo(mutableSetOf()) { it.key() }
        val css = MarkupStyles(colors)
        styles =
            merge(
                objects(existing?.get("styles")),
                styleRules.map { (id, rules, sheet) ->
                    sheet.located { mapOf("id" to id, "name" to id, "value" to css.surface(rules)) }
                },
            )
    }

    private fun readSheet(sheet: Element) {
        sheet.only()
        val source = sheet.textOnly().trimEnd()
        var offset = 0
        while (offset < source.length) {
            val match = rule.matchAt(source, offset) ?: invalid("Unsupported CSS rule at stylesheet offset $offset")
            val selector = match.groupValues[1]
            val declarations = MarkupStyles.declarations(match.groupValues[2])
            if (selector == ":root") {
                declareTokens(declarations)
            } else {
                styleRules.add(SurfaceRule(selector.drop(1), declarations, sheet))
            }
            offset = match.range.last + 1
        }
    }

    private fun declareTokens(declarations: Map<String, String>) {
        declarations.forEach { (key, value) -> token(key, value) }
    }

    private fun token(
        key: String,
        value: String,
    ) {
        if (!key.startsWith("--") || !MarkupStyles.hex.matches(value)) invalid("Expected --name and a hex color")
        val id = identifier(key.removePrefix("--"))
        tokenRules.add(mapOf("id" to id, "name" to id, "type" to "color", "value" to value))
    }

    fun fields(existing: Fields?): Fields =
        buildMap {
            putAll(existing.orEmpty())
            put("kind", "tokens")
            put("entries", this@MarkupLibrary.entries)
            if (styles.isNotEmpty()) put("styles", styles)
        }
}
