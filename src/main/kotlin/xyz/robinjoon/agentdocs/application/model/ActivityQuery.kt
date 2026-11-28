package xyz.robinjoon.agentdocs.application.model

import java.util.UUID

// spaceIds null includes entries outside spaces; an empty set matches nothing. The filter's since is exclusive.
// Both ID bounds are exclusive. oldestFirst serves feeds continuing by unique log ID with an unchanged since.
data class ActivityQuery(
    val spaceIds: Set<UUID>?,
    val beforeId: Long?,
    val limit: Int,
    val oldestFirst: Boolean,
    val afterId: Long? = null,
    val filter: ActivityFilter = ActivityFilter(),
)
