package xyz.robinjoon.agentdocs.workspace.domain.thread

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.time.Instant
import java.util.UUID

class CommentThreadTest {
    private val now = Instant.parse("2026-10-18T09:00:00Z")
    private val userId = UUID.randomUUID()
    private val person = Actor.human(userId, "robin")
    private val claude = Actor.agent(userId, "robin", AgentIdentity(UUID.randomUUID(), "Claude Code"))
    private val codex = Actor.agent(userId, "robin", AgentIdentity(UUID.randomUUID(), "Codex"))
    private val location = ThreadLocation(SpaceId(UUID.randomUUID()), DocumentId(UUID.randomUUID()), Anchor.Document, 3)
    private val opening = Comment.write(CommentId(UUID.randomUUID()), person, "Please shorten the delay", now)
    private val thread = CommentThread.open(ThreadId(UUID.randomUUID()), location, opening, now)

    @Test
    fun `an agent answers a person's thread but only the person resolves it`() {
        val answered = requireNotNull(thread.reply(comment(claude, "Done"), now.plusSeconds(60)).markAnswered(now.plusSeconds(60)))
        val resolved = requireNotNull(answered.resolve(person, now.plusSeconds(120)))

        assertThat(answered.status).isEqualTo(ThreadStatus.ANSWERED)
        assertThatThrownBy { answered.resolve(claude, now) }.isInstanceOf(ThreadActionException::class.java)
        assertThat(resolved.status).isEqualTo(ThreadStatus.RESOLVED)
        assertThat(resolved.progress.timeline.resolvedBy).isEqualTo(person)
    }

    @Test
    fun `an agent may resolve a thread an agent opened`() {
        val agentThread = CommentThread.open(ThreadId(UUID.randomUUID()), location, comment(claude, "Is this still true?"), now)

        assertThat(agentThread.resolve(codex, now)?.status).isEqualTo(ThreadStatus.RESOLVED)
    }

    @Test
    fun `a person's reply opens an answered or resolved thread again and clears the resolution`() {
        val resolved = requireNotNull(thread.resolve(person, now.plusSeconds(10)))
        val reopened = resolved.reply(comment(person, "One more thing"), now.plusSeconds(20))

        assertThat(reopened.status).isEqualTo(ThreadStatus.OPEN)
        assertThat(reopened.progress.timeline.resolvedAt).isNull()
    }

    @Test
    fun `only the author changes a comment, two agents of one person are different authors, and edits keep the old body`() {
        val withReply = thread.reply(comment(claude, "Done"), now)
        val reply = withReply.comments.last()
        val edited = withReply.editComment(reply.id, claude, "Done, set to 30s", now.plusSeconds(5))

        assertThatThrownBy { withReply.editComment(reply.id, codex, "x", now) }
            .isInstanceOf(ThreadActionException::class.java)
            .hasMessage("only the author can change a comment")
        assertThat(edited.comments.last().body).isEqualTo("Done, set to 30s")
        assertThat(
            edited.comments
                .last()
                .history.edits
                .map { it.previousBody },
        ).containsExactly("Done")
    }

    @Test
    fun `a deleted comment keeps its place but not its words`() {
        val deleted = thread.deleteComment(opening.id, person, now.plusSeconds(5))

        assertThat(deleted.comments.single().isDeleted).isTrue()
        assertThat(deleted.comments.single().body).isEmpty()
        assertThatThrownBy { deleted.deleteComment(opening.id, person, now) }.hasMessage("the comment was deleted")
    }

    @Test
    fun `linking the same revision twice changes nothing`() {
        val linked = requireNotNull(thread.linkRevision(4, now))

        assertThat(linked.progress.linkedRevisions).containsExactly(4)
        assertThat(linked.linkRevision(4, now)).isNull()
    }

    private fun comment(
        author: Actor,
        body: String,
    ): Comment = Comment.write(CommentId(UUID.randomUUID()), author, body, now)
}
