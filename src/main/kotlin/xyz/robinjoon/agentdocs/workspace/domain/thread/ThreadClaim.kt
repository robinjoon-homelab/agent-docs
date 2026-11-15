package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant

data class ThreadClaim(
    val actor: Actor,
    val acquiredAt: Instant,
    val expiresAt: Instant,
) {
    init {
        require(actor.isAgent) { "only agent connections claim threads" }
    }

    fun active(at: Instant): Boolean = at.isBefore(expiresAt)

    fun ownedBy(by: Actor): Boolean = actor.agent?.connectionId == by.agent?.connectionId
}
