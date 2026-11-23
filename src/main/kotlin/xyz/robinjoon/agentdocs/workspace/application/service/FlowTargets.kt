package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.FlowQuery
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAppearance
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.util.Locale

internal class FlowTargetList(
    val parts: Sequence<String>,
    val matches: Int,
)

// Screens and elements of a deployed flow document that match a query, one line each, in document order. Only the
// returned candidates are rendered for their hash; the rest of the document is never serialized.
internal object FlowTargets {
    fun list(
        document: Document,
        query: FlowQuery,
        link: ((FlowScreen, FlowElement?) -> String)?,
    ): FlowTargetList {
        val settings =
            document.blocks.firstOrNull() as? FlowSettings ?: throw InvalidRequestException(
                "flowQuery reads the deployed flow format; this document uses the v4 design model",
                "use the v4 design tools for this document",
            )
        val kind = KindFilter.of(query.kind)
        val scope = query.screen?.let { screenOf(document.blocks, it) }
        val matches = candidates(document.blocks, query, kind, scope)
        val shown = matches.drop(query.offset).take(query.limit)
        val counts = FlowOutline.elementCounts(document.blocks)
        val lines = shown.asSequence().map { line(it, counts, settings, link) }
        return FlowTargetList(sequenceOf(header(document, query, matches.size)) + lines + footer(query, matches.size), matches.size)
    }

    private class Candidate(
        val block: Block,
        val screen: FlowScreen,
        val fields: List<String>,
    )

    private fun candidates(
        blocks: List<Block>,
        query: FlowQuery,
        kind: KindFilter,
        scope: FlowScreen?,
    ): List<Candidate> {
        val needle = query.text?.lowercase(Locale.ROOT)
        val found = mutableListOf<Candidate>()
        var current: FlowScreen? = null
        for (block in blocks) {
            if (block is FlowScreen) current = block
            val screen = current ?: continue
            if (scope != null && scope.id != screen.id) continue
            val wanted =
                when (block) {
                    is FlowScreen -> kind.screens
                    is FlowElement -> kind.accepts(block)
                    else -> false
                }
            if (!wanted) continue
            val fields = matchedFields(block, needle) ?: continue
            found += Candidate(block, screen, fields)
        }
        return found
    }

    // Field names a person can recognise, in the order they appear in the markdown. null means no match.
    private fun matchedFields(
        block: Block,
        needle: String?,
    ): List<String>? {
        if (needle == null) return emptyList()
        val fields =
            when (block) {
                is FlowScreen -> {
                    listOf(
                        "id" to listOf(block.screenId),
                        "name" to listOf(block.name),
                        "entry" to listOf(block.entry),
                        "desc" to listOf(block.desc),
                    )
                }

                is FlowElement -> {
                    listOf(
                        "label" to listOf(block.text.label),
                        "value" to listOf(block.text.value),
                        "item" to block.appearance.items.orEmpty(),
                        "date" to listOfNotNull(block.appearance.date),
                        "state" to block.behavior.states,
                        "when" to block.behavior.actions.map { it.whenText },
                        "to" to block.behavior.actions.mapNotNull { it.to },
                        "desc" to listOf(block.text.desc),
                    )
                }

                else -> {
                    emptyList()
                }
            }
        return fields.filter { (_, values) -> values.any { it.lowercase(Locale.ROOT).contains(needle) } }.map { it.first }.ifEmpty { null }
    }

    private fun screenOf(
        blocks: List<Block>,
        reference: String,
    ): FlowScreen =
        blocks.filterIsInstance<FlowScreen>().firstOrNull { it.id.value == reference || it.screenId.equals(reference, ignoreCase = true) }
            ?: throw InvalidRequestException(
                "screen $reference is not in the document",
                "read the outline (format outline) to find screen ids",
            )

    private fun line(
        candidate: Candidate,
        counts: Map<BlockId, Int>,
        settings: FlowSettings,
        link: ((FlowScreen, FlowElement?) -> String)?,
    ): String {
        val block = candidate.block
        val match = if (candidate.fields.isEmpty()) "" else " · match: " + candidate.fields.joinToString(", ")
        val described =
            if (block is FlowElement) {
                val screen = candidate.screen
                val note =
                    when {
                        !block.behavior.annotated -> " · 모양만"
                        block.text.desc.isBlank() -> " · 설명 없음"
                        else -> " · 설명 있음"
                    }
                val targets =
                    block.behavior.actions
                        .mapNotNull { it.to }
                        .distinct()
                val moves = if (targets.isEmpty()) "" else " · → " + targets.joinToString(", ")
                "element ${FlowOutline.elementSummary(block)} · in ${screen.screenId} ${screen.name} (b:${screen.id})$note$moves"
            } else {
                "screen ${FlowOutline.screenSummary(candidate.screen, counts[block.id] ?: 0, settings)}"
            }
        val url = link?.let { " " + it(candidate.screen, block as? FlowElement) }.orEmpty()
        return "- $described$match (b:${block.id} h:${BlockMarkdown.hash(block)})$url\n"
    }

    private fun header(
        document: Document,
        query: FlowQuery,
        total: Int,
    ): String {
        val criteria =
            listOfNotNull(
                query.text?.let { "text \"$it\"" },
                query.kind?.let { "kind $it" },
                query.screen?.let { "screen $it" },
            ).takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")").orEmpty()
        val first = query.offset + 1
        val last = minOf(query.offset + query.limit, total)
        val range = if (first > last) "0" else "$first–$last"
        return "flowQuery: $range of $total matches$criteria at revision ${document.revision}\n"
    }

    private fun footer(
        query: FlowQuery,
        total: Int,
    ): Sequence<String> {
        val next = query.offset + query.limit
        return when {
            total == 0 -> sequenceOf("- no match: try shorter text, another kind, or read the outline\n")
            query.offset >= total -> sequenceOf("- offset ${query.offset} is past the last match ($total)\n")
            next < total -> sequenceOf("- … ${total - next} more: continue with offset $next, or narrow with text, kind or screen\n")
            else -> emptySequence()
        }
    }

    // screen and element select a block type; any other value must be an element kind.
    private class KindFilter(
        val screens: Boolean,
        private val elements: Boolean,
        private val kind: String?,
    ) {
        fun accepts(element: FlowElement): Boolean = elements && (kind == null || element.appearance.kind == kind)

        companion object {
            fun of(value: String?): KindFilter =
                when {
                    value == null -> KindFilter(screens = true, elements = true, kind = null)

                    value == "screen" -> KindFilter(screens = true, elements = false, kind = null)

                    value == "element" -> KindFilter(screens = false, elements = true, kind = null)

                    FlowAppearance.isKind(value) -> KindFilter(screens = false, elements = true, kind = value)

                    else -> throw InvalidRequestException(
                        "unknown flow kind $value",
                        "use screen, element, or one of: " + FlowAppearance.kindNames().joinToString(", "),
                    )
                }
        }
    }
}
