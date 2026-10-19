package xyz.robinjoon.agentdocs.application.model

import java.time.Instant

// One entry of the activity log that every context appends to. kind is a dotted name such as "document.revised".
data class ActivityRecord(
    val kind: String,
    val actor: ActivityActor,
    val subject: ActivitySubject,
    val summary: String,
    val at: Instant,
)
