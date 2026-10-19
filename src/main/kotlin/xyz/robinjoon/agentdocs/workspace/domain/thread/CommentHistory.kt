package xyz.robinjoon.agentdocs.workspace.domain.thread

import java.time.Instant
import java.util.Collections

// When a comment was written, its earlier bodies, and when it was deleted.
@ConsistentCopyVisibility
data class CommentHistory private constructor(
    val createdAt: Instant,
    val edits: List<CommentEdit>,
    val deletedAt: Instant?,
) {
    fun edited(edit: CommentEdit): CommentHistory = CommentHistory(createdAt, Collections.unmodifiableList(edits + edit), deletedAt)

    // A deleted comment no longer keeps the text it had.
    fun deleted(at: Instant): CommentHistory = CommentHistory(createdAt, emptyList(), at)

    companion object {
        fun of(
            createdAt: Instant,
            edits: List<CommentEdit>,
            deletedAt: Instant?,
        ): CommentHistory = CommentHistory(createdAt, Collections.unmodifiableList(edits.toList()), deletedAt)
    }
}
