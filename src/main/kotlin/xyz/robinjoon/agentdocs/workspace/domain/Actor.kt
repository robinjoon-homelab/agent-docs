package xyz.robinjoon.agentdocs.workspace.domain

import java.util.UUID

// Who performs a change. Revisions, comments, and activity keep the same value, so history shows a person as
// "@login" and an agent as "client name · @login" without asking another context.
data class Actor(
    val kind: ActorKind,
    val userId: UUID,
    val login: String,
    val agent: AgentIdentity?,
) {
    init {
        if (login.isBlank()) {
            throw InvalidActorException("login must not be blank")
        }
        if ((kind == ActorKind.AGENT) != (agent != null)) {
            throw InvalidActorException("only an agent actor carries an agent identity")
        }
    }

    val isAgent: Boolean get() = kind == ActorKind.AGENT

    // Two agents of the same person are different authors, so ownership compares the connection as well.
    fun isSameAuthorAs(other: Actor): Boolean =
        kind == other.kind && userId == other.userId && agent?.connectionId == other.agent?.connectionId

    companion object {
        fun human(
            userId: UUID,
            login: String,
        ): Actor = Actor(ActorKind.HUMAN, userId, login, null)

        fun agent(
            userId: UUID,
            login: String,
            agent: AgentIdentity,
        ): Actor = Actor(ActorKind.AGENT, userId, login, agent)
    }
}
