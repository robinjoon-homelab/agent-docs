package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant

data class SuggestionBasis(
    val anchor: Anchor,
    val hash: String,
    val before: String,
    val after: String,
) {
    init {
        if (anchor == Anchor.Document || before.isEmpty() || before.length > 100_000 || after.length > 100_000) {
            throw InvalidThreadException("a suggestion targets text, a cell, or a block with up to 100000 characters")
        }
    }
}

data class SuggestionDecision(
    val actor: Actor,
    val at: Instant,
    val accepted: Boolean,
    val revision: Int?,
)

data class Suggestion(
    val comment: CommentId,
    val basis: SuggestionBasis,
    val decision: SuggestionDecision? = null,
) {
    fun decide(decision: SuggestionDecision): Suggestion {
        if (this.decision != null) throw ThreadActionException("suggestion already decided", "read the current suggestion")
        return copy(decision = decision)
    }
}
