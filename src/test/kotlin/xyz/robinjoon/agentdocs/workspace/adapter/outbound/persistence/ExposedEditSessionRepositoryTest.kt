package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget
import xyz.robinjoon.agentdocs.workspace.domain.editsession.HumanEditSessions
import xyz.robinjoon.agentdocs.workspace.domain.editsession.HumanInputBatch
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputTrace
import java.util.UUID

class ExposedEditSessionRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedEditSessionRepository()
    private val target = EditSessionTarget(WorkspaceFixtures.documentId, WorkspaceFixtures.spaceId, WorkspaceFixtures.agent, null)
    private val at = WorkspaceFixtures.now

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `an open session round trips and a closed one leaves the open lookup`() {
        val saved =
            database.inTransaction {
                repository.lock(target.scope)
                repository.save(EditSession.start(target, at))
            }
        assertThat(database.inTransaction { repository.open(target.scope) }).isEqualTo(saved)
        val closed = database.inTransaction { repository.save(saved.close(at.plusSeconds(5), EditEndReason.EXPLICIT)) }
        assertThat(database.inTransaction { repository.open(target.scope) }).isNull()
        assertThat(database.inTransaction { repository.list(target.documentId) }).containsExactly(closed)
    }

    @Test
    fun `the original response snapshot survives later session updates`() {
        val saved = database.inTransaction { repository.save(EditSession.start(target, at)) }
        database.inTransaction {
            repository.remember(target.scope, "start:op", saved)
            repository.save(saved.close(at.plusSeconds(5), EditEndReason.EXPLICIT))
        }
        assertThat(database.inTransaction { repository.replay(target.scope, "start:op") }).isEqualTo(saved)
        assertThat(database.inTransaction { repository.find(saved.id) }?.isOpen).isFalse()
    }

    @Test
    fun `both committed document version and batch identity prevent duplicate adoption`() {
        val batch = UUID.randomUUID()
        assertThat(database.inTransaction { repository.adopt(target.documentId, 5, batch) }).isTrue()
        assertThat(database.inTransaction { repository.adopt(target.documentId, 5, UUID.randomUUID()) }).isFalse()
        assertThat(database.inTransaction { repository.adopt(target.documentId, 6, batch) }).isFalse()
        assertThat(database.inTransaction { repository.adopt(target.documentId, 6, UUID.randomUUID()) }).isTrue()
    }

    @Test
    fun `human supersession round trips without removing the absorbed record`() {
        val human = target.copy(actor = WorkspaceFixtures.owner, tabId = UUID.randomUUID())
        val first = EditSession.start(human, at).close(at, EditEndReason.HUMAN)
        val second = EditSession.start(human, at.plusSeconds(200)).supersede(first.id)
        database.inTransaction {
            repository.lock(human.scope)
            repository.save(first)
            repository.save(second)
        }
        val stored = database.inTransaction { repository.inScope(human.scope) }
        assertThat(stored).hasSize(2)
        assertThat(stored.single { it.id == second.id }.supersededBy).isEqualTo(first.id)
        assertThat(database.inTransaction { repository.find(second.id) }).isEqualTo(second)
    }

    @Test
    fun `one stored batch marker covers both foreground scopes and retry cannot add results`() {
        val tabA = UUID.randomUUID()
        val tabB = UUID.randomUUID()
        val humanA = target.copy(actor = WorkspaceFixtures.owner, tabId = tabA)
        val first = EditSession.start(humanA, at.minusSeconds(480)).close(at, EditEndReason.HUMAN)
        database.inTransaction { repository.save(first) }
        val batch =
            HumanInputBatch(
                UUID.randomUUID(),
                listOf(
                    InputTrace(tabA, UUID.randomUUID(), at.minusSeconds(420), at.minusSeconds(420)),
                    InputTrace(tabB, UUID.randomUUID(), at.minusSeconds(120), at.minusSeconds(120)),
                ),
            )
        database.inTransaction {
            assertThat(repository.adopt(target.documentId, 5, batch.batchId)).isTrue()
            for (trace in batch.foregrounds()) {
                val foreground = humanA.copy(tabId = trace.tabId)
                val changes = HumanEditSessions.adopt(foreground, repository.inScope(foreground.scope), trace, at)
                changes.forEach { repository.save(it) }
            }
        }
        val stored = database.inTransaction { repository.list(target.documentId) }
        assertThat(stored.map { it.target.tabId }).containsExactlyInAnyOrder(tabA, tabB)
        assertThat(EditStatistics.of(stored).human.milliseconds).isEqualTo(60_000)
        assertThat(EditStatistics.of(stored).human.count).isEqualTo(2)
        assertThat(database.inTransaction { repository.adopt(target.documentId, 5, UUID.randomUUID()) }).isFalse()
        assertThat(database.inTransaction { repository.adopt(target.documentId, 6, batch.batchId) }).isFalse()
        assertThat(database.inTransaction { repository.list(target.documentId) }).containsExactlyInAnyOrderElementsOf(stored)
    }
}
