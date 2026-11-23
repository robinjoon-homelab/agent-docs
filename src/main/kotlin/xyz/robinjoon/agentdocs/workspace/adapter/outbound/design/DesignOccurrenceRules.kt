package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.DesignValidationWarning
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

internal object DesignOccurrenceRules {
    fun validate(
        root: JsonNode,
        previous: JsonNode?,
    ): List<DesignValidationWarning> {
        val prior = previous?.let { collect(it, DesignInputFacts(null)) }.orEmpty()
        val warnings = linkedSetOf<DesignValidationWarning>()
        val facts =
            DesignInputFacts(prior) { fact, reason ->
                warnings.add(DesignValidationWarning(fact.site.first(), "orphanedReference", "${fact.site.joinToString("/")}: $reason"))
            }
        collect(root, facts)
        return warnings.toList()
    }

    private fun collect(
        root: JsonNode,
        facts: DesignInputFacts,
    ): Set<DesignInputFact> {
        val blocks = root.path("blocks").associateBy { it.designString("id") }
        val inputs = DesignInputRules(facts)
        val declarations = DesignInputDeclarations(blocks, inputs)
        blocks.values.forEach { declarations.check(it) }
        val projections = DesignProjections(blocks, inputs)
        blocks.values.forEach { projections.check(it) }
        return facts.collected()
    }
}

internal data class DesignInputFact(
    val site: List<String>,
    val value: JsonNode,
)

internal class DesignInputFacts(
    private val previous: Set<DesignInputFact>?,
    private val retained: (DesignInputFact, String) -> Unit = { _, _ -> },
) {
    private val seen = HashSet<DesignInputFact>()

    fun track(
        site: List<String>,
        value: JsonNode,
    ): DesignInputFact {
        val fact = DesignInputFact(site, value)
        if (previous == null) seen.add(fact)
        return fact
    }

    fun missing(
        fact: DesignInputFact,
        reason: String,
    ) {
        if (previous == null) return
        if (fact !in previous) reject(reason)
        retained(fact, reason)
    }

    val checking: Boolean get() = previous != null

    fun collected(): Set<DesignInputFact> = seen

    fun invalid(reason: String) {
        if (previous != null) reject(reason)
    }

    private fun reject(reason: String): Nothing = throw InvalidDocumentException(reason)
}

internal fun JsonNode.designString(name: String): String = path(name).asString("")

internal fun JsonNode.designFields(): Map<String, JsonNode> = properties().associate { it.key to it.value }

internal fun JsonNode.designProps(): Map<String, JsonNode> = path("props").associateBy { it.designString("id") }

internal fun JsonNode.isDesignBinding(): Boolean = isObject && (has("prop") || (has("repeat") && has("field")))

internal fun JsonNode.designChildren(): Sequence<JsonNode> =
    sequenceOf("children", "fallback", "template", "operands").flatMap { path(it).asSequence() } + listOfNotNull(get("source")).asSequence()
