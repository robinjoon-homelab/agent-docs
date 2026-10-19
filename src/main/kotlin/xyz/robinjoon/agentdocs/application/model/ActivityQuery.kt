package xyz.robinjoon.agentdocs.application.model

import java.time.Instant
import java.util.UUID

// spaceIds null means every space plus the entries outside spaces; an empty set matches nothing. since keeps entries
// strictly after that moment and beforeId entries before that id. oldestFirst serves feeds that continue from a
// moment; otherwise the newest entries come first.
data class ActivityQuery(
    val spaceIds: Set<UUID>?,
    val since: Instant?,
    val beforeId: Long?,
    val limit: Int,
    val oldestFirst: Boolean,
)
