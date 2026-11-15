package xyz.robinjoon.agentdocs.application.model

import java.time.Instant

// One entry of the activity log that every context appends to. kind is a dotted name such as "document.revised".
// createdComment is set only by the entry that created a comment.
data class ActivityRecord(
    val kind: String,
    val actor: ActivityActor,
    val subject: ActivitySubject,
    val summary: String,
    val at: Instant,
    val createdComment: CreatedComment? = null,
)
