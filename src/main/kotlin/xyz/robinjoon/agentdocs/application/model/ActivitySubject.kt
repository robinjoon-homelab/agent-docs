package xyz.robinjoon.agentdocs.application.model

import java.util.UUID

// What the activity concerns. A space id is set for everything inside a space, so access rules can filter the log.
data class ActivitySubject(
    val spaceId: UUID?,
    val documentId: UUID?,
    val threadId: UUID?,
    val revision: Int?,
)
