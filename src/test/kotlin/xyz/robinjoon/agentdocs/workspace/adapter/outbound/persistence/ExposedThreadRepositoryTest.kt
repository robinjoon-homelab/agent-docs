package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.person
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadCriteria
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.util.UUID

class ExposedThreadRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedThreadRepository()
    private val space = database.inTransaction { ExposedSpaceRepository().add(space("agent-docs")) }
    private val document =
        document(space.id, "notes", emptyList()).also { created ->
            database.inTransaction { ExposedDocumentRepository().add(created, revisionOf(created)) }
        }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `every anchor kind round trips with comments, edits, resolution, and linked revisions`() {
        val anchors =
            listOf(
                Anchor.Text(BlockId("para01"), 3, 8, "delay"),
                Anchor.Cell(BlockId("tabl01"), BlockId("row001"), BlockId("colm01"), "30s"),
                Anchor.Whole(BlockId("imag01")),
                Anchor.Document,
            )
        anchors.forEach { anchor ->
            val opened = thread(anchor, person, "Please @octocat check")
            val worked =
                requireNotNull(
                    opened
                        .reply(comment(agent, "Done"), at)
                        .editComment(opened.comments.first().id, person, "Please @octocat check again", at)
                        .linkRevision(1, at)
                        ?.resolve(person, at),
                )

            database.inTransaction { repository.add(opened) }
            val stored = database.inTransaction { repository.update(worked) }

            assertThat(stored).isEqualTo(worked)
        }
    }

    @Test
    fun `a query filters by status, author kind, mention, and change time`() {
        val asked = database.inTransaction { repository.add(thread(Anchor.Document, person, "Hey @Octocat")) }
        val agentOpened = database.inTransaction { repository.add(thread(Anchor.Document, agent, "Is this still true?")) }
        database.inTransaction { repository.update(requireNotNull(agentOpened.resolve(agent, at.plusSeconds(10)))) }

        val open = find(ThreadQuery(setOf(space.id), null, setOf(ThreadStatus.OPEN), null, null, null))
        val byAgents = find(ThreadQuery(null, document.id, null, ActorKind.AGENT, null, null))
        val mentioned = find(ThreadQuery(null, null, null, null, "octocat", null))
        val recent = find(ThreadQuery(null, null, null, null, null, at.plusSeconds(5)))

        assertThat(open.map { it.id }).containsExactly(asked.id)
        assertThat(byAgents.map { it.id }).containsExactly(agentOpened.id)
        assertThat(mentioned.map { it.id }).containsExactly(asked.id)
        assertThat(recent.map { it.id }).containsExactly(agentOpened.id)
        assertThat(find(ThreadQuery(emptySet(), null, null, null, null, null))).isEmpty()
    }

    @Test
    fun `a comment finds its thread and revisions list the threads linked to them`() {
        val opened = database.inTransaction { repository.add(thread(Anchor.Document, person, "Fix this")) }
        database.inTransaction { repository.update(requireNotNull(opened.linkRevision(1, at))) }

        assertThat(database.inTransaction { repository.findByCommentForUpdate(opened.comments.single().id) }?.id).isEqualTo(opened.id)
        assertThat(database.inTransaction { repository.linksByRevision(document.id) }).isEqualTo(mapOf(1 to listOf(opened.id)))
        assertThat(database.inTransaction { repository.findByDocument(document.id) }.map { it.id }).containsExactly(opened.id)
    }

    private fun find(query: ThreadQuery): List<CommentThread> = database.inTransaction { repository.find(ThreadCriteria(query, 10)) }

    private fun thread(
        anchor: Anchor,
        author: Actor,
        body: String,
    ): CommentThread =
        CommentThread.open(ThreadId(UUID.randomUUID()), ThreadLocation(space.id, document.id, anchor, 1), comment(author, body), at)

    private fun comment(
        author: Actor,
        body: String,
    ): Comment = Comment.write(CommentId(UUID.randomUUID()), author, body, at)
}
