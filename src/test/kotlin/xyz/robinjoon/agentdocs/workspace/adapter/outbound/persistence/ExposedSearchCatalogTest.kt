package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.person
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

class ExposedSearchCatalogTest {
    private val database = MigratedH2Database()
    private val catalog = ExposedSearchCatalog()
    private val documents = ExposedDocumentRepository()
    private val space = database.inTransaction { ExposedSpaceRepository().add(space("docs")) }
    private val other = database.inTransaction { ExposedSpaceRepository().add(space("other")) }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `body text matches in any case and percent or underscore match only themselves`() {
        val retry = add(document(space.id, "retry", listOf(paragraph("para01", "결제 Retry 100% 완료"))))
        add(document(space.id, "noise", listOf(paragraph("para01", "100 percent and a_b"))))

        assertThat(database.inTransaction { catalog.documents(setOf(space.id), "retry", 10) }).containsExactly(retry.id)
        assertThat(database.inTransaction { catalog.documents(setOf(space.id), "100%", 10) }).containsExactly(retry.id)
        assertThat(database.inTransaction { catalog.documents(setOf(space.id), "a_", 10) }).hasSize(1)
        assertThat(database.inTransaction { catalog.documents(setOf(other.id), "retry", 10) }).isEmpty()
    }

    @Test
    fun `trashed documents and their comments are not found, titles match on their own`() {
        val live = add(document(space.id, "live", emptyList()))
        val gone = add(document(space.id, "gone", emptyList()))
        val trashed = requireNotNull(gone.trash(at))
        database.inTransaction { documents.update(trashed, revisionOf(trashed), amended = false) }
        listOf(live, gone).forEach { target ->
            val comment = Comment.write(CommentId(UUID.randomUUID()), person, "지연 시간은?", at)
            database.inTransaction {
                ExposedThreadRepository().add(
                    CommentThread.open(ThreadId(UUID.randomUUID()), ThreadLocation(space.id, target.id, Anchor.Document, 1), comment, at),
                )
            }
        }

        assertThat(database.inTransaction { catalog.titles(setOf(space.id), "TITLE OF", 10) }).containsExactly(live.id)
        assertThat(database.inTransaction { catalog.comments(setOf(space.id), "지연", 10) }.map { it.documentId }).containsExactly(live.id)
    }

    private fun add(document: Document): Document = database.inTransaction { documents.add(document, revisionOf(document)) }
}
