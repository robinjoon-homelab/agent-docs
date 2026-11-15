package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import java.util.Collections

// Origin is a value snapshot, including references supplied by mutable request collections.
class ChangeOrigin(
    val actor: Actor,
    val source: ChangeSource,
    codeReferences: List<CodeReference> = emptyList(),
    val importProvenance: ImportProvenance? = null,
) {
    val codeReferences: List<CodeReference> = Collections.unmodifiableList(codeReferences.toList())

    fun withReferences(references: List<CodeReference>): ChangeOrigin = ChangeOrigin(actor, source, references, importProvenance)

    override fun equals(other: Any?): Boolean =
        other is ChangeOrigin &&
            actor == other.actor && source == other.source && codeReferences == other.codeReferences &&
            importProvenance == other.importProvenance

    override fun hashCode(): Int = listOf(actor, source, codeReferences, importProvenance).hashCode()
}
