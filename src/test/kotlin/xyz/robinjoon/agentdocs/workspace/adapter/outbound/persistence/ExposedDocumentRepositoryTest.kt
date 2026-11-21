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
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListPosition
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.SampleBlocks

class ExposedDocumentRepositoryTest {
    private val database = MigratedH2Database()
    private val documents = ExposedDocumentRepository()
    private val revisions = ExposedRevisionCatalog()
    private val space = database.inTransaction { ExposedSpaceRepository().add(space("agent-docs")) }

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `flow type survives document revision and listing round trips`() {
        val base = document(space.id, "flow", emptyList())
        val flow =
            xyz.robinjoon.agentdocs.workspace.domain.document.Document.create(
                base.id,
                base.header.copy(type = "flow"),
                xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
                    .initial("phone", "M"),
                at,
            )
        val stored = database.inTransaction { documents.add(flow, revisionOf(flow)) }
        assertThat(stored).isEqualTo(flow)
        assertThat(
            database
                .inTransaction { revisions.find(flow.id, 1) }
                ?.content
                ?.header
                ?.type,
        ).isEqualTo("flow")
        assertThat(
            database
                .inTransaction { documents.list(setOf(space.id), DocumentFilter(null, null, null, false), 10) }
                .single()
                .header.type,
        ).isEqualTo("flow")
    }

    @Test
    fun `every block type round trips with its ids and the first revision holds the same content`() {
        val created = document(space.id, "design/blocks", SampleBlocks.all)

        val stored = database.inTransaction { documents.add(created, revisionOf(created)) }

        assertThat(stored).isEqualTo(created)
        assertThat(database.inTransaction { revisions.find(created.id, 1) }?.content?.blocks).isEqualTo(SampleBlocks.all)
    }

    @Test
    fun `pending edits update content versions while confirmed history remains immutable`() {
        val created = document(space.id, "notes", listOf(paragraph("para01", "one")))
        database.inTransaction { documents.add(created, revisionOf(created)) }
        val typed = created.amend(created.header, listOf(paragraph("para01", "two")), at.plusSeconds(5))
        val pending = revisionOf(typed).copy(number = 2)
        database.inTransaction { documents.savePending(typed, pending) }
        val continued = typed.amend(typed.header, listOf(paragraph("para01", "three")), at.plusSeconds(10))
        val incoming = revisionOf(continued)
        val latest = incoming.copy(number = 2, change = pending.change.amendedWith(incoming.change, incoming.change.summary))
        database.inTransaction { documents.savePending(continued, latest) }

        assertThat(database.inTransaction { documents.findById(created.id) }).isEqualTo(continued)
        assertThat(continued.version).isEqualTo(3)
        assertThat(continued.revision).isEqualTo(1)
        assertThat(database.inTransaction { documents.pending(created.id) }).isEqualTo(latest)
        assertThat(database.inTransaction { revisions.find(created.id, 2) }).isNull()
        assertThat(database.inTransaction { revisions.list(created.id, RevisionFilter(null, null, null)) }.map { it.number })
            .containsExactly(1)
        assertThat(database.inTransaction { revisions.find(created.id, 1) }?.content?.blocks).isEqualTo(created.blocks)

        val confirmed = database.inTransaction { documents.confirmPending(continued) }

        assertThat(confirmed.revision).isEqualTo(2)
        assertThat(confirmed.version).isEqualTo(3)
        assertThat(database.inTransaction { documents.pending(created.id) }).isNull()
        assertThat(database.inTransaction { revisions.find(created.id, 2) }).isEqualTo(latest)
        assertThat(database.inTransaction { documents.confirmPending(confirmed) }).isEqualTo(confirmed)
    }

    @Test
    fun `an amendment cannot overwrite a confirmed revision`() {
        val created = document(space.id, "notes", listOf(paragraph("para01", "one")))
        database.inTransaction { documents.add(created, revisionOf(created)) }
        val changed = created.amend(created.header, listOf(paragraph("para01", "replacement")), at.plusSeconds(1))

        assertThatThrownBy { database.inTransaction { documents.update(changed, revisionOf(changed), amended = true) } }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(database.inTransaction { revisions.find(created.id, 1) }?.content?.blocks).isEqualTo(created.blocks)
        assertThat(database.inTransaction { documents.findById(created.id) }).isEqualTo(created)
    }

    @Test
    fun `pending expiry includes exactly thirty idle seconds and ten minutes total`() {
        val idle = document(space.id, "idle", emptyList())
        val capped = document(space.id, "capped", emptyList())
        val active = document(space.id, "active", emptyList())
        database.inTransaction {
            listOf(idle, capped, active).forEach { documents.add(it, revisionOf(it)) }
            val idleEdit = idle.amend(idle.header, listOf(paragraph("para01", "idle")), at)
            documents.savePending(idleEdit, revisionOf(idleEdit).copy(number = 2))
            val cappedEdit = capped.amend(capped.header, listOf(paragraph("para01", "capped")), at.plusSeconds(29))
            val cappedRevision =
                revisionOf(cappedEdit).let {
                    it.copy(
                        number = 2,
                        change =
                            RevisionChange.restore(
                                it.change.origin,
                                it.change.summary,
                                it.change.changedBlocks,
                                it.change.period.copy(createdAt = at.minusSeconds(570)),
                            ),
                    )
                }
            documents.savePending(cappedEdit, cappedRevision)
            val activeEdit = active.amend(active.header, listOf(paragraph("para01", "active")), at.plusSeconds(1))
            documents.savePending(activeEdit, revisionOf(activeEdit).copy(number = 2))
        }

        assertThat(database.inTransaction { documents.expiredPending(at.plusSeconds(29)) }).isEmpty()
        assertThat(database.inTransaction { documents.expiredPending(at.plusSeconds(30)) }).containsExactlyInAnyOrder(idle.id, capped.id)
    }

    @Test
    fun `a revision keeps what made it`() {
        val created = document(space.id, "restored", listOf(paragraph("para01", "one")))
        database.inTransaction { documents.add(created, revisionOf(created)) }
        val restored = created.revise(created.header, listOf(paragraph("para01", "zero")), at.plusSeconds(5))
        val restore = revisionOf(restored).let { it.copy(change = it.change.withKind(RevisionKind.RESTORE)) }

        database.inTransaction { documents.update(restored, restore, amended = false) }

        assertThat(database.inTransaction { revisions.find(created.id, 1) }?.change?.kind).isEqualTo(RevisionKind.EDIT)
        assertThat(database.inTransaction { revisions.find(created.id, 2) }?.change?.kind).isEqualTo(RevisionKind.RESTORE)
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
    fun `continuation keeps the same folder and returns timestamp ties without duplicates`() {
        val children = (1..4).map { document(space.id, "payments/$it", emptyList()) }
        val sibling = document(space.id, "payments-other/child", emptyList())
        database.inTransaction { (children + sibling).forEach { documents.add(it, revisionOf(it)) } }
        val filter = DocumentFilter(null, null, "payments", false)
        val first = database.inTransaction { documents.list(setOf(space.id), filter, 2) }
        val last = first.last()
        val after = DocumentListPosition(last.lifecycle.updatedAt, last.id.value)
        val second = database.inTransaction { documents.list(setOf(space.id), filter.copy(after = after), 3) }

        assertThat(first + second).hasSize(4)
        assertThat((first + second).map { it.id }).containsExactlyInAnyOrderElementsOf(children.map { it.id })
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
