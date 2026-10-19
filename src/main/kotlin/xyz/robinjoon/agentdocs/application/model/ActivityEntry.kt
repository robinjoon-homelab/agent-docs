package xyz.robinjoon.agentdocs.application.model

// A stored record with its position in the log. Ids grow with insertion, so they also work as a paging cursor.
data class ActivityEntry(
    val id: Long,
    val record: ActivityRecord,
)
