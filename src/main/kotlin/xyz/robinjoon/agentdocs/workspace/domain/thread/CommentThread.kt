package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant
import java.util.Collections

// A conversation attached to one place in a document. The first comment's author opened the thread. Agents may mark
// a thread answered but never resolve one a person opened: the person who asked decides when it is done. The private
// constructor and copy keep changes behind the methods; a method returns null when nothing would change.
@AggregateRoot
@ConsistentCopyVisibility
data class CommentThread private constructor(
    val id: ThreadId,
    val location: ThreadLocation,
    val progress: ThreadProgress,
    val comments: List<Comment>,
) {
    init {
        if (comments.isEmpty()) {
            throw InvalidThreadException("a thread starts with a comment")
        }
    }

    val author: Actor get() = comments.first().author

    val status: ThreadStatus get() = progress.status

    fun comment(id: CommentId): Comment? = comments.firstOrNull { it.id == id }

    // A person replying to an answered or resolved thread opens it again, since the reply asks for more.
    fun reply(
        comment: Comment,
        at: Instant,
    ): CommentThread {
        if (comments.any { it.id == comment.id }) {
            throw InvalidThreadException("comment ${comment.id} is already in the thread")
        }
        val reopens = status == ThreadStatus.RESOLVED || (status == ThreadStatus.ANSWERED && !comment.author.isAgent)
        return copy(progress = progress.moved(if (reopens) ThreadStatus.OPEN else status, at), comments = snapshot(comments + comment))
    }

    fun markAnswered(at: Instant): CommentThread? =
        if (status ==
            ThreadStatus.OPEN
        ) {
            copy(progress = progress.moved(ThreadStatus.ANSWERED, at))
        } else {
            null
        }

    fun resolve(
        by: Actor,
        at: Instant,
    ): CommentThread? {
        if (by.isAgent && !author.isAgent) {
            throw ThreadActionException(
                "an agent cannot resolve a thread a person opened",
                "mark the thread answered; the person who opened it resolves it",
            )
        }
        return if (status == ThreadStatus.RESOLVED) null else copy(progress = progress.resolved(by, at))
    }

    fun reopen(at: Instant): CommentThread? =
        if (status ==
            ThreadStatus.OPEN
        ) {
            null
        } else {
            copy(progress = progress.moved(ThreadStatus.OPEN, at))
        }

    fun editComment(
        id: CommentId,
        by: Actor,
        body: String,
        at: Instant,
    ): CommentThread = replaceComment(ownComment(id, by).edit(body, at), at)

    fun deleteComment(
        id: CommentId,
        by: Actor,
        at: Instant,
    ): CommentThread = replaceComment(ownComment(id, by).delete(at), at)

    fun linkRevision(
        revision: Int,
        at: Instant,
    ): CommentThread? = if (revision in progress.linkedRevisions) null else copy(progress = progress.linked(revision, at))

    fun moveTo(
        anchor: Anchor,
        revision: Int,
        at: Instant,
    ): CommentThread = copy(location = location.copy(anchor = anchor, anchoredRevision = revision), progress = progress.touched(at))

    private fun ownComment(
        id: CommentId,
        by: Actor,
    ): Comment {
        val comment = comment(id) ?: throw InvalidThreadException("comment $id is not in thread ${this.id}")
        if (!comment.author.isSameAuthorAs(by)) {
            throw ThreadActionException("only the author can change a comment", "reply with a new comment instead")
        }
        if (comment.isDeleted) {
            throw ThreadActionException("the comment was deleted", "reply with a new comment instead")
        }
        return comment
    }

    private fun replaceComment(
        comment: Comment,
        at: Instant,
    ): CommentThread = copy(comments = snapshot(comments.map { if (it.id == comment.id) comment else it }), progress = progress.touched(at))

    companion object {
        fun open(
            id: ThreadId,
            location: ThreadLocation,
            first: Comment,
            at: Instant,
        ): CommentThread = CommentThread(id, location, ThreadProgress.opened(at), listOf(first))

        fun restore(
            id: ThreadId,
            location: ThreadLocation,
            progress: ThreadProgress,
            comments: List<Comment>,
        ): CommentThread = CommentThread(id, location, progress, snapshot(comments))

        private fun snapshot(comments: List<Comment>): List<Comment> = Collections.unmodifiableList(comments.toList())
    }
}
