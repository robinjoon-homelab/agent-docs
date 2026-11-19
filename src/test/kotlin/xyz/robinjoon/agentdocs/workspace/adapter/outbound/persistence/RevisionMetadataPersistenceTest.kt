package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionLabel
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.CodeReference
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ImportProvenance
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange

class RevisionMetadataPersistenceTest {
    private val database = MigratedH2Database()
    private val documents = ExposedDocumentRepository()
    private val revisions = ExposedRevisionCatalog()
    private val space = database.inTransaction { ExposedSpaceRepository().add(PersistenceFixtures.space("history")) }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `labels change independently while revision content origin and versions remain identical`() {
        val document = PersistenceFixtures.document(space.id, "one", listOf(PersistenceFixtures.paragraph("block1", "Text")))
        val reference = CodeReference("owner/repo", "a".repeat(40), 7)
        val provenance = ImportProvenance("obsidian", "folder/note.md")
        val origin = ChangeOrigin(PersistenceFixtures.agent, ChangeSource.MCP, listOf(reference), provenance)
        val change = RevisionChange.of(origin, "Imported", document.blocks.map { it.id }, PersistenceFixtures.at)
        val revision = Revision.of(document, change)
        database.inTransaction { documents.add(document, revision) }
        database.inTransaction { documents.nameRevision(document.id, 1, "Review") }
        val named = database.inTransaction { revisions.find(document.id, 1) }
        assertThat(named).isEqualTo(revision.copy(name = "Review"))
        assertThat(database.inTransaction { documents.findById(document.id) }).isEqualTo(document)
        assertThat(
            database
                .inTransaction {
                    revisions.list(
                        document.id,
                        xyz.robinjoon.agentdocs.workspace.application.model
                            .RevisionFilter(null, null, null),
                    )
                }.single()
                .name,
        ).isEqualTo("Review")
        database.inTransaction { documents.nameRevision(document.id, 1, null) }
        assertThat(database.inTransaction { revisions.find(document.id, 1) }).isEqualTo(revision)
    }

    @Test
    fun `label operation snapshots replay the original label and are removed with their document`() {
        val document = PersistenceFixtures.document(space.id, "one", emptyList())
        val key = OperationKey("writer", "label")
        val label = RevisionLabel(document.id, 2, "Original")
        val record = OperationRecord("revision.name", document.id.value, 2, state = OperationResultState.AVAILABLE, revisionLabel = label)
        val operations = ExposedOperationStore()
        database.inTransaction { operations.save(key, record, PersistenceFixtures.at) }
        assertThat(database.inTransaction { operations.find(key) }).isEqualTo(record)
        database.inTransaction { operations.removeDocumentResults(mapOf(document.id to document.spaceId)) }
        val removed = database.inTransaction { operations.find(key) }
        assertThat(removed?.purgedFrom).isEqualTo(document.spaceId)
        assertThat(removed?.state).isEqualTo(OperationResultState.REMOVED)
        assertThat(removed?.revisionLabel).isNull()
    }
}
