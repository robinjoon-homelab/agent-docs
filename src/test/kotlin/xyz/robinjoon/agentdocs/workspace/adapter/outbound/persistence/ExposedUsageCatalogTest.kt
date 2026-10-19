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
import xyz.robinjoon.agentdocs.workspace.application.model.WeeklyUsage
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ExposedUsageCatalogTest {
    private val database = MigratedH2Database()
    private val catalog = ExposedUsageCatalog()
    private val views = ExposedDocumentViewReporter()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `weekly counts, agent response times, and documents opened again on another day`() {
        val space = database.inTransaction { ExposedSpaceRepository().add(space("docs")) }
        val created = document(space.id, "notes", emptyList())
        database.inTransaction { ExposedDocumentRepository().add(created, revisionOf(created)) }
        val question = Comment.write(CommentId(UUID.randomUUID()), person, "Shorter?", at)
        val thread = CommentThread.open(ThreadId(UUID.randomUUID()), ThreadLocation(space.id, created.id, Anchor.Document, 1), question, at)
        val answered = thread.reply(Comment.write(CommentId(UUID.randomUUID()), agent, "Done", at.plusSeconds(600)), at.plusSeconds(600))
        database.inTransaction { ExposedThreadRepository().add(answered) }
        database.inTransaction {
            views.record(created.id, "robin", LocalDate.of(2026, 10, 18))
            views.record(created.id, "robin", LocalDate.of(2026, 10, 18))
            views.record(created.id, "robin", LocalDate.of(2026, 10, 19))
        }

        val facts = database.inTransaction { catalog.since(Instant.parse("2026-10-12T00:00:00Z")) }

        assertThat(facts.weeks).containsExactly(WeeklyUsage(LocalDate.of(2026, 10, 12), space.id, 1, 2))
        assertThat(facts.agentResponseTimes).containsExactly(Duration.ofMinutes(10))
        assertThat(facts.revisitedDocuments).isEqualTo(1)
    }
}
