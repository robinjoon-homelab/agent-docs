package xyz.robinjoon.agentdocs.workspace.domain.editsession

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import java.util.UUID

class EditSessionTest {
    private val at = WorkspaceFixtures.now
    private val human =
        EditSessionTarget(WorkspaceFixtures.documentId, WorkspaceFixtures.spaceId, WorkspaceFixtures.owner, UUID.randomUUID())
    private val agent = EditSessionTarget(WorkspaceFixtures.documentId, WorkspaceFixtures.spaceId, WorkspaceFixtures.agent, null)

    @Test
    fun `a single input ends at the same instant and expires at exactly 120 seconds`() {
        val session = EditSession.start(human, at)
        assertThat(session.expired(at.plusSeconds(119))).isFalse()
        assertThat(session.expired(at.plusSeconds(120))).isTrue()
        assertThat(session.close(at.plusSeconds(120), EditEndReason.HUMAN).period.endedAt).isEqualTo(at)
    }

    @Test
    fun `human time uses actual input timestamps and excludes trailing idle`() {
        val trace = InputTrace(requireNotNull(human.tabId), UUID.randomUUID(), at.plusSeconds(2), at.plusSeconds(9))
        val session = EditSession.start(human, at).input(trace).close(at.plusSeconds(130), EditEndReason.HUMAN)
        assertThat(session.period.startedAt).isEqualTo(at)
        assertThat(session.period.endedAt).isEqualTo(at.plusSeconds(9))
    }

    @Test
    fun `agents expire at 30 minutes of inactivity and later end cannot change the result`() {
        val active = EditSession.start(agent, at).activity(at.plusSeconds(20))
        assertThat(active.expired(at.plusSeconds(1819))).isFalse()
        assertThat(active.expired(at.plusSeconds(1820))).isTrue()
        val estimated = active.close(at.plusSeconds(1820), EditEndReason.ESTIMATED)
        assertThat(estimated.period.endedAt).isEqualTo(at.plusSeconds(20))
        assertThat(estimated.close(at.plusSeconds(1900), EditEndReason.EXPLICIT)).isEqualTo(estimated)
    }

    @Test
    fun `an explicit agent end includes thinking and waiting time`() {
        val ended = EditSession.start(agent, at).close(at.plusSeconds(75), EditEndReason.EXPLICIT)
        assertThat(ended.period.endedAt).isEqualTo(at.plusSeconds(75))
        assertThat(ended.period.reason).isEqualTo(EditEndReason.EXPLICIT)
    }

    @Test
    fun `reversed and future input times are rejected`() {
        assertThatThrownBy { InputTrace(UUID.randomUUID(), UUID.randomUUID(), at, at.minusSeconds(1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val future = InputTrace(UUID.randomUUID(), UUID.randomUUID(), at, at.plusSeconds(1))
        assertThatThrownBy { future.requireValidAt(at) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
