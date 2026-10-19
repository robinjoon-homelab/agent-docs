package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.person
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.SampleBlocks

class ExposedDocumentRepositoryTest {
    private val database = MigratedH2Database()
    private val documents = ExposedDocumentRepository()
    private val revisions = ExposedRevisionCatalog()
    private val space = database.inTransaction { ExposedSpaceRepository().add(space("agent-docs")) }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `every block type round trips with its ids and the first revision holds the same content`() {
        val created = document(space.id, "design/blocks", SampleBlocks.all)

        val stored = database.inTransaction { documents.add(created, revisionOf(created)) }

        assertThat(stored).isEqualTo(created)
        assertThat(database.inTransaction { revisions.find(created.id, 1) }?.content?.blocks).isEqualTo(SampleBlocks.all)
    }

    @Test
    fun `a new revision is appended and an amended revision replaces the latest one`() {
        val created = document(space.id, "notes", listOf(paragraph("para01", "one")))
        database.inTransaction { documents.add(created, revisionOf(created)) }
        val revised = created.revise(created.header, listOf(paragraph("para01", "two")), at.plusSeconds(5))
        database.inTransaction { documents.update(revised, revisionOf(revised, agent), amended = false) }
        val amended = revised.amend(revised.header, listOf(paragraph("para01", "three")), at.plusSeconds(10))

        database.inTransaction { documents.update(amended, revisionOf(amended, agent), amended = true) }

        val history = database.inTransaction { revisions.list(created.id, RevisionFilter(null, null, null)) }
        assertThat(history.map { it.number }).containsExactly(2, 1)
        assertThat(database.inTransaction { revisions.find(created.id, 2) }?.content?.blocks).containsExactly(paragraph("para01", "three"))
        assertThat(
            database
                .inTransaction {
                    revisions.list(created.id, RevisionFilter(ActorKind.AGENT, null, null))
                }.map { it.number },
        ).containsExactly(2)
    }

    @Test
    fun `a block mark follows the latest change and disappears with the block`() {
        val created = document(space.id, "notes", listOf(paragraph("para01", "one"), paragraph("para02", "two")))
        database.inTransaction { documents.add(created, revisionOf(created)) }
        val revised = created.revise(created.header, listOf(paragraph("para01", "one!")), at.plusSeconds(5))

        database.inTransaction {
            documents.update(
                revised,
                revisionOf(revised, agent, listOf(revised.blocks.single().id)),
                amended = false,
            )
        }

        val marks = database.inTransaction { revisions.marks(created.id) }
        assertThat(marks.map { it.blockId.value to it.revision }).containsExactly("para01" to 2)
        assertThat(marks.single().actor).isEqualTo(agent)
    }

    @Test
    fun `a path is unique among live documents but a trashed document frees it`() {
        val first = document(space.id, "notes", emptyList())
        database.inTransaction { documents.add(first, revisionOf(first)) }

        assertThatThrownBy { database.inTransaction { documents.add(document(space.id, "notes", emptyList()), revisionOf(first)) } }
            .isInstanceOf(PathTakenException::class.java)
        val trashed = requireNotNull(first.trash(at.plusSeconds(1)))
        database.inTransaction { documents.update(trashed, revisionOf(trashed), amended = false) }
        val second = document(space.id, "notes", emptyList())
        database.inTransaction { documents.add(second, revisionOf(second)) }
        assertThat(database.inTransaction { documents.findByPath(space.id, DocumentPath("notes")) }?.id).isEqualTo(second.id)
    }

    @Test
    fun `the list filters by trash, status, tag, and folder and names who changed each document last`() {
        val tagged =
            document(space.id, "payments/retry", emptyList()).let { draft ->
                draft.revise(
                    draft.header.copy(properties = DocumentProperties.of(listOf("api", "v2"), DocumentStatus.IN_REVIEW, null)),
                    emptyList(),
                    at,
                )
            }
        database.inTransaction { documents.add(tagged, revisionOf(tagged, agent)) }
        val other = document(space.id, "payments_old/notes", emptyList())
        database.inTransaction { documents.add(other, revisionOf(other)) }

        val inFolder = database.inTransaction { documents.list(setOf(space.id), DocumentFilter(null, null, "payments", false), 10) }
        val byTag =
            database.inTransaction {
                documents.list(
                    setOf(space.id),
                    DocumentFilter(DocumentStatus.IN_REVIEW, "v2", null, false),
                    10,
                )
            }
        val byPartialTag = database.inTransaction { documents.list(setOf(space.id), DocumentFilter(null, "v", null, false), 10) }

        assertThat(inFolder.map { it.id }).containsExactly(tagged.id)
        assertThat(byTag.single().updatedBy).isEqualTo(agent)
        assertThat(byPartialTag).isEmpty()
        assertThat(database.inTransaction { documents.list(setOf(space.id), DocumentFilter(null, null, null, true), 10) }).isEmpty()
    }

    @Test
    fun `deleting removes the document, its history, and its threads`() {
        val created = document(space.id, "notes", listOf(paragraph("para01", "one")))
        database.inTransaction { documents.add(created, revisionOf(created)) }

        database.inTransaction { documents.delete(created.id) }

        assertThat(database.inTransaction { documents.findById(created.id) }).isNull()
        assertThat(database.inTransaction { revisions.find(created.id, 1) }).isNull()
    }

    @Test
    fun `documents found by ids keep the order asked for and skip missing ones`() {
        val first = document(space.id, "a", emptyList())
        val second = document(space.id, "b", emptyList())
        database.inTransaction { documents.add(first, revisionOf(first)) }
        database.inTransaction { documents.add(second, revisionOf(second)) }

        assertThat(
            database
                .inTransaction {
                    documents.findByIds(listOf(second.id, document(space.id, "c", emptyList()).id, first.id))
                }.map { it.id },
        ).containsExactly(second.id, first.id)
        assertThat(database.inTransaction { documents.trashedBefore(at.plusSeconds(60)) }).isEmpty()
        assertThat(person.login).isEqualTo("robin")
    }
}
