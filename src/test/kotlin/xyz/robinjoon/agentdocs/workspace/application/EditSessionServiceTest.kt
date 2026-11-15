package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.application.model.SavedEditInput
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.EditSessionRepository
import xyz.robinjoon.agentdocs.workspace.application.service.EditSessionService
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget
import xyz.robinjoon.agentdocs.workspace.domain.editsession.HumanInputBatch
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputTrace
import java.util.UUID

class EditSessionServiceTest {
    private val sessions = mockk<EditSessionRepository>()
    private val documents = mockk<DocumentRepository>()
    private val service = EditSessionService(sessions, documents, WorkspaceFixtures.clock)
    private val actor = WorkspaceFixtures.agentAccess(Permission.EDIT, setOf(WorkspaceFixtures.spaceId))
    private val context = WorkspaceFixtures.context(actor, ChangeSource.MCP, "operation")
    private val document = WorkspaceFixtures.document(emptyList(), 5)
    private val target = EditSessionTarget(document.id, document.spaceId, WorkspaceFixtures.agent, null)

    init {
        every { documents.findById(document.id) } returns document
        every { sessions.lock(any()) } returns Unit
        every { sessions.replay(any(), any()) } returns null
        every { sessions.inScope(any()) } returns emptyList()
        every { sessions.save(any()) } answers { firstArg() }
        every { sessions.remember(any(), any(), any()) } answers { thirdArg() }
    }

    @Test
    fun `repeated start returns the same open session ID`() {
        val existing = EditSession.start(target, WorkspaceFixtures.now.minusSeconds(20))
        every { sessions.open(any()) } returns existing
        assertThat(service.start(context, document.id).id).isEqualTo(existing.id)
    }

    @Test
    fun `an expired session ends at last activity and a late explicit end cannot change it`() {
        val existing = EditSession.start(target, WorkspaceFixtures.now.minusSeconds(1900))
        every { sessions.find(existing.id) } returns existing
        val result = service.end(context, existing.id)
        assertThat(result.period.reason).isEqualTo(EditEndReason.ESTIMATED)
        assertThat(result.period.endedAt).isEqualTo(existing.period.lastActivityAt)
    }

    @Test
    fun `statistics enforce current document visibility`() {
        val outside = WorkspaceFixtures.agentAccess(Permission.EDIT, emptySet())
        assertThatThrownBy { service.statistics(outside, document.id) }.isInstanceOf(DocumentNotFoundException::class.java)
    }

    @Test
    fun `duplicate committed input leaves the recorded statistics unchanged`() {
        val trace = InputTrace(UUID.randomUUID(), UUID.randomUUID(), WorkspaceFixtures.now, WorkspaceFixtures.now)
        every { sessions.adopt(any(), any(), any()) } returns false
        assertThat(service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, true, trace))).isEmpty()
    }

    @Test
    fun `a stale tab close leaves later input open`() {
        val trace = InputTrace(UUID.randomUUID(), UUID.randomUUID(), WorkspaceFixtures.now, WorkspaceFixtures.now)
        val human = EditSessionTarget(document.id, document.spaceId, WorkspaceFixtures.owner, trace.tabId)
        val open = EditSession.start(human, WorkspaceFixtures.now)
        every { sessions.open(human.scope) } returns open
        val result = service.closeHuman(WorkspaceFixtures.ownerAccess, document.id, trace.tabId, WorkspaceFixtures.now.minusSeconds(1))
        assertThat(result).isEqualTo(open)
    }

    @Test
    fun `a future input is rejected before adoption`() {
        val trace = InputTrace(UUID.randomUUID(), UUID.randomUUID(), WorkspaceFixtures.now, WorkspaceFixtures.now.plusSeconds(1))
        assertThatThrownBy { service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, true, trace)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a committed changed input preserves real timestamps rather than service time`() {
        val at = WorkspaceFixtures.now
        val trace = InputTrace(UUID.randomUUID(), UUID.randomUUID(), at.minusSeconds(10), at.minusSeconds(2))
        every { sessions.adopt(any(), any(), any()) } returns true
        val saved = service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, true, trace)).lastOrNull()
        assertThat(saved?.period?.startedAt).isEqualTo(trace.firstInputAt)
        assertThat(saved?.period?.lastActivityAt).isEqualTo(trace.lastInputAt)
        assertThat(saved?.isOpen).isTrue()
    }

    @Test
    fun `no-op input is not meaningful even when HTTP saving succeeded`() {
        val trace = InputTrace(UUID.randomUUID(), UUID.randomUUID(), WorkspaceFixtures.now, WorkspaceFixtures.now)
        assertThat(service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, false, trace))).isEmpty()
    }

    @Test
    fun `an input at the 120-second boundary begins a fresh interval`() {
        val at = WorkspaceFixtures.now
        val tab = UUID.randomUUID()
        val human = EditSessionTarget(document.id, document.spaceId, WorkspaceFixtures.owner, tab)
        val old = EditSession.start(human, at.minusSeconds(120))
        every { sessions.adopt(any(), any(), any()) } returns true
        every { sessions.inScope(human.scope) } returns listOf(old)
        val trace = InputTrace(tab, UUID.randomUUID(), at, at)
        val saved = service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, true, trace)).lastOrNull()
        assertThat(saved?.id).isNotEqualTo(old.id)
        assertThat(saved?.period?.startedAt).isEqualTo(at)
    }

    @Test
    fun `end cannot use another connection's session`() {
        val other =
            WorkspaceFixtures.agent.copy(
                agent =
                    xyz.robinjoon.agentdocs.workspace.domain
                        .AgentIdentity(UUID.randomUUID(), "other"),
            )
        val different = EditSession.start(target.copy(actor = other), WorkspaceFixtures.now)
        every { sessions.find(different.id) } returns different
        assertThatThrownBy { service.end(context, different.id) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `an exact start operation replay preserves the first response after the session ended`() {
        val original = EditSession.start(target, WorkspaceFixtures.now.minusSeconds(20))
        val closed = original.close(WorkspaceFixtures.now, EditEndReason.EXPLICIT)
        every { sessions.replay(any(), "start:operation") } returns original
        every { sessions.find(original.id) } returns closed
        assertThat(service.start(context, document.id)).isEqualTo(original)
        assertThat(service.start(context, document.id).period.endedAt).isNull()
    }

    @Test
    fun `a late committed input corrects the same human interval after idle expiry`() {
        val now = WorkspaceFixtures.now
        val tab = UUID.randomUUID()
        val human = EditSessionTarget(document.id, document.spaceId, WorkspaceFixtures.owner, tab)
        val first = EditSession.start(human, now.minusSeconds(121)).close(now.minusSeconds(1), EditEndReason.HUMAN)
        val trace = InputTrace(tab, UUID.randomUUID(), now.minusSeconds(61), now.minusSeconds(61))
        every { sessions.adopt(any(), any(), any()) } returns true
        every { sessions.inScope(human.scope) } returns listOf(first)
        val corrected = service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, true, trace)).single()
        assertThat(corrected.id).isEqualTo(first.id)
        assertThat(corrected.period.startedAt).isEqualTo(now.minusSeconds(121))
        assertThat(corrected.period.lastActivityAt).isEqualTo(now.minusSeconds(61))
        assertThat(corrected.isOpen).isTrue()
    }

    @Test
    fun `one successful version adopts old and new foreground input with one batch identity`() {
        val now = WorkspaceFixtures.now
        val tabA = UUID.randomUUID()
        val tabB = UUID.randomUUID()
        val humanA = EditSessionTarget(document.id, document.spaceId, WorkspaceFixtures.owner, tabA)
        val first = EditSession.start(humanA, now.minusSeconds(480)).close(now, EditEndReason.HUMAN)
        val traceA = InputTrace(tabA, UUID.randomUUID(), now.minusSeconds(420), now.minusSeconds(420))
        val traceB = InputTrace(tabB, UUID.randomUUID(), now.minusSeconds(120), now.minusSeconds(120))
        val batch = HumanInputBatch(UUID.randomUUID(), listOf(traceB, traceA))
        every { sessions.inScope(humanA.scope) } returns listOf(first)
        every { sessions.adopt(document.id, 5, batch.batchId) } returns true
        val input = SavedEditInput(document.id, 5, true, batch)
        val saved = service.recordHuman(WorkspaceFixtures.ownerAccess, input)
        assertThat(saved.map { it.target.tabId }).containsExactlyInAnyOrder(tabA, tabB)
        val summary = EditStatistics.of(saved)
        assertThat(summary.human.milliseconds).isEqualTo(60_000)
        assertThat(summary.human.count).isEqualTo(2)
        every { sessions.adopt(document.id, 5, batch.batchId) } returns false
        assertThat(service.recordHuman(WorkspaceFixtures.ownerAccess, input)).isEmpty()
    }

    @Test
    fun `one invalid foreground rejects the whole batch and no-op accepts none`() {
        val now = WorkspaceFixtures.now
        val valid = InputTrace(UUID.randomUUID(), UUID.randomUUID(), now, now)
        val future = InputTrace(UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(1), now.plusSeconds(1))
        val batch = HumanInputBatch(UUID.randomUUID(), listOf(valid, future))
        assertThatThrownBy { service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, true, batch)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(service.recordHuman(WorkspaceFixtures.ownerAccess, SavedEditInput(document.id, 5, false, batch))).isEmpty()
    }
}
