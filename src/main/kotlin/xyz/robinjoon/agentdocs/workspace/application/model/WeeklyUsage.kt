package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.time.LocalDate

// How many documents were written or changed, and how many comments were written, in one space in one week starting
// on Monday (UTC). A document changed forty times in a week counts once.
data class WeeklyUsage(
    val weekStart: LocalDate,
    val spaceId: SpaceId,
    val documents: Int,
    val comments: Int,
)
