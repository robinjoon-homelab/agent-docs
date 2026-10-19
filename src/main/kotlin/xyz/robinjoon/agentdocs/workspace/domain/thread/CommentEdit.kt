package xyz.robinjoon.agentdocs.workspace.domain.thread

import java.time.Instant

// The body a comment had before an edit.
data class CommentEdit(
    val previousBody: String,
    val editedAt: Instant,
)
