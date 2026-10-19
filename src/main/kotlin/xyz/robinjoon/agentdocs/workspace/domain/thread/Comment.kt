package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant

// A comment body is markdown. A deleted comment keeps its place in the thread as "삭제된 댓글" but no longer
// holds its text or its earlier versions.
data class Comment(
    val id: CommentId,
    val author: Actor,
    val body: String,
    val history: CommentHistory,
) {
    init {
        if (history.deletedAt == null && (body.isBlank() || body.length > MAX_BODY_LENGTH || body != body.trim())) {
            throw InvalidThreadException("a comment is 1 to $MAX_BODY_LENGTH characters")
        }
    }

    val isDeleted: Boolean get() = history.deletedAt != null

    val editedAt: Instant? get() = history.edits.lastOrNull()?.editedAt

    fun edit(
        body: String,
        at: Instant,
    ): Comment = Comment(id, author, body.trim(), history.edited(CommentEdit(this.body, at)))

    fun delete(at: Instant): Comment = Comment(id, author, "", history.deleted(at))

    companion object {
        const val MAX_BODY_LENGTH = 10_000

        fun write(
            id: CommentId,
            author: Actor,
            body: String,
            at: Instant,
        ): Comment = Comment(id, author, body.trim(), CommentHistory.of(at, emptyList(), null))
    }
}
