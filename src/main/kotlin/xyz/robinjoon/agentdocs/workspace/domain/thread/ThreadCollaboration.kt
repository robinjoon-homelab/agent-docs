package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant
import java.util.Collections

@ConsistentCopyVisibility
data class ThreadCollaboration private constructor(
    val claim: ThreadClaim?,
    val reactions: List<ThreadReaction>,
    val suggestions: List<Suggestion>,
) {
    fun requireAvailable(
        by: Actor,
        at: Instant,
    ) {
        if (by.isAgent && claim?.active(at) == true && !claim.ownedBy(by)) {
            throw ThreadActionException("thread is claimed by another connection", "wait until the claim expires or is released")
        }
    }

    fun claim(
        by: Actor,
        at: Instant,
        action: String,
    ): ThreadCollaboration {
        if (!by.isAgent) throw ThreadActionException("only an agent connection can claim", "use an agent connection")
        requireAvailable(by, at)
        if (action != "acquire" && claim != null && !claim.ownedBy(by)) {
            throw ThreadActionException("only the claiming connection can renew or release", "acquire after expiry")
        }
        val current = claim?.takeIf { it.active(at) }
        val next =
            when (action) {
                "acquire" -> current ?: ThreadClaim(by, at, at.plusSeconds(900))
                "renew" -> renewed(current, at)
                "release" -> null
                else -> throw InvalidThreadException("claim action must be acquire, renew, or release")
            }
        return copy(claim = next)
    }

    private fun renewed(
        current: ThreadClaim?,
        at: Instant,
    ): ThreadClaim {
        if (current == null) throw ThreadActionException("claim expired", "acquire the thread again")
        return current.copy(expiresAt = at.plusSeconds(900))
    }

    fun released(): ThreadCollaboration = copy(claim = null)

    fun react(
        by: Actor,
        emoji: String,
        add: Boolean,
    ): ThreadCollaboration {
        val reaction = ThreadReaction(by, emoji)
        val remaining = reactions.filterNot { it.emoji == emoji && it.actor.isSameAuthorAs(by) }
        return copy(reactions = immutable(if (add) remaining + reaction else remaining))
    }

    fun suggested(suggestion: Suggestion): ThreadCollaboration = copy(suggestions = immutable(suggestions + suggestion))

    fun decided(
        comment: CommentId,
        decision: SuggestionDecision,
    ): ThreadCollaboration = copy(suggestions = immutable(suggestions.map { if (it.comment == comment) it.decide(decision) else it }))

    fun deleted(comment: CommentId): ThreadCollaboration = copy(suggestions = immutable(suggestions.filterNot { it.comment == comment }))

    companion object {
        fun empty(): ThreadCollaboration = ThreadCollaboration(null, emptyList(), emptyList())

        fun restore(
            claim: ThreadClaim?,
            reactions: List<ThreadReaction>,
            suggestions: List<Suggestion>,
        ): ThreadCollaboration = ThreadCollaboration(claim, immutable(reactions), immutable(suggestions))

        private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
    }
}
