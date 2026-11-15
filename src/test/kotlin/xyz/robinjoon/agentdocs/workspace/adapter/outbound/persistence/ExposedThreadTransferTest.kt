package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database

class ExposedThreadTransferTest {
    private val database = MigratedH2Database()
    private val fixture = TransferPersistenceFixtures(database)
    private val threads = ExposedThreadRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `moving thread ownership preserves comments anchors linked revisions and comment history`() {
        val doc = fixture.document(fixture.source, "one", "Text")
        val thread = fixture.comment(doc, "Original", "Edited")
        database.inTransaction { threads.moveDocuments(setOf(doc.id), fixture.target.id) }
        val moved = database.inTransaction { threads.findById(thread.id) }
        assertThat(moved?.id).isEqualTo(thread.id)
        assertThat(moved?.location).isEqualTo(thread.location.copy(spaceId = fixture.target.id))
        assertThat(moved?.comments).isEqualTo(thread.comments)
        assertThat(moved?.progress).isEqualTo(thread.progress)
        assertThat(moved?.collaboration).isEqualTo(thread.collaboration)
    }
}
