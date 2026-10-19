package xyz.robinjoon.agentdocs.workspace.application.model

import java.time.Duration

data class UsageFacts(
    val weeks: List<WeeklyUsage>,
    val agentResponseTimes: List<Duration>,
    val revisitedDocuments: Int,
)
