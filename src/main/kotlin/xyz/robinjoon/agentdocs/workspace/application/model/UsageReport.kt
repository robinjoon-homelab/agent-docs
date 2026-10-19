package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import java.time.Duration

// What the owner home shows: activity per space per week, how long agents take to answer a person's thread, and how
// many documents people came back to on a later day.
data class UsageReport(
    val weeks: List<WeeklyUsage>,
    val spaces: List<Space>,
    val agentResponseMedian: Duration?,
    val revisitedDocuments: Int,
)
