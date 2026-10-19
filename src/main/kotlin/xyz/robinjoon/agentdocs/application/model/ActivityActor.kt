package xyz.robinjoon.agentdocs.application.model

import java.util.UUID

// Who did something, in the plain form every context can write: a person, an agent acting for a person, or the
// system itself for scheduled work.
data class ActivityActor(
    val kind: ActivityActorKind,
    val userId: UUID?,
    val login: String?,
    val connectionId: UUID?,
    val clientName: String?,
) {
    companion object {
        val system = ActivityActor(ActivityActorKind.SYSTEM, null, null, null, null)
    }
}
