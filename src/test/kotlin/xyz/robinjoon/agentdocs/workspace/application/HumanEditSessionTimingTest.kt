package xyz.robinjoon.agentdocs.workspace.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget
import xyz.robinjoon.agentdocs.workspace.domain.editsession.HumanEditSessions
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputInterval
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputTrace
import java.util.UUID

// Pure timing policy and public summary, without services, database, or transaction simulation.
class HumanEditSessionTimingTest {
    private val at = WorkspaceFixtures.now
    private val target =
        EditSessionTarget(WorkspaceFixtures.documentId, WorkspaceFixtures.spaceId, WorkspaceFixtures.owner, UUID.randomUUID())

    @Test
    fun `two isolated inputs in one successful batch count zero seconds and two intervals`() {
        val trace = trace(0, 300)
        val adopted = HumanEditSessions.adopt(target, emptyList(), trace, at.plusSeconds(301))
        val summary = EditStatistics.of(close(adopted))
        assertThat(trace.intervals).hasSize(2)
        assertThat(summary.human.milliseconds).isZero()
        assertThat(summary.human.count).isEqualTo(2)
    }

    @Test
    fun `expiry before or after a delayed successful input has the same final public time`() {
        val first = EditSession.start(target, at)
        val beforeExpiry = HumanEditSessions.adopt(target, listOf(first), trace(60), at.plusSeconds(119))
        val expired = first.close(at.plusSeconds(120), EditEndReason.HUMAN)
        val afterExpiry = HumanEditSessions.adopt(target, listOf(expired), trace(60), at.plusSeconds(121))
        assertThat(afterExpiry.single().id).isEqualTo(first.id)
        assertThat(afterExpiry.single().period).isEqualTo(beforeExpiry.single().period)
        assertThat(EditStatistics.of(close(afterExpiry))).isEqualTo(EditStatistics.of(close(beforeExpiry)))
        assertThat(EditStatistics.of(close(afterExpiry)).human.milliseconds).isEqualTo(60_000)
        assertThat(EditStatistics.of(close(afterExpiry)).human.count).isEqualTo(1)
    }

    @Test
    fun `a late bridging input corrects one canonical interval and preserves absorbed records`() {
        val first = EditSession.start(target, at).close(at, EditEndReason.HUMAN)
        val second = EditSession.start(target, at.plusSeconds(200)).close(at.plusSeconds(200), EditEndReason.HUMAN)
        val changes = HumanEditSessions.adopt(target, listOf(first, second), trace(100), at.plusSeconds(400))
        assertThat(changes).hasSize(2)
        val canonical = changes.single { !it.isSuperseded }
        assertThat(canonical.id).isEqualTo(first.id)
        assertThat(changes.single { it.isSuperseded }.supersededBy).isEqualTo(first.id)
        val summary = EditStatistics.of(changes)
        assertThat(summary.human.milliseconds).isEqualTo(200_000)
        assertThat(summary.human.count).isEqualTo(1)
    }

    @Test
    fun `exactly 120 seconds separates intervals while 119 seconds joins them`() {
        assertThat(trace(0, 119).intervals).hasSize(1)
        assertThat(trace(0, 120).intervals).hasSize(2)
        val first = EditSession.start(target, at).close(at, EditEndReason.HUMAN)
        val separate = HumanEditSessions.adopt(target, listOf(first), trace(120), at.plusSeconds(241))
        assertThat(separate.single().id).isNotEqualTo(first.id)
        assertThat(EditStatistics.of(listOf(first) + separate).human.count).isEqualTo(2)
    }

    @Test
    fun `different foreground scopes cannot fill background time`() {
        val old = EditSession.start(target.copy(tabId = UUID.randomUUID()), at).close(at, EditEndReason.HUMAN)
        assertThatThrownBy { HumanEditSessions.adopt(target, listOf(old), trace(60), at.plusSeconds(121)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val fresh = HumanEditSessions.adopt(target, emptyList(), trace(60), at.plusSeconds(121))
        val summary = EditStatistics.of(listOf(old) + close(fresh))
        assertThat(summary.human.milliseconds).isZero()
        assertThat(summary.human.count).isEqualTo(2)
    }

    @Test
    fun `late older inputs join on actual time instead of arrival order`() {
        val latest = EditSession.start(target, at.plusSeconds(60)).close(at.plusSeconds(60), EditEndReason.HUMAN)
        val corrected = HumanEditSessions.adopt(target, listOf(latest), trace(0), at.plusSeconds(200))
        assertThat(EditStatistics.of(corrected).human.milliseconds).isEqualTo(60_000)
        assertThat(corrected.single().period.startedAt).isEqualTo(at)
    }

    @Test
    fun `a separate later interval closes an idle predecessor instead of leaving two open roots`() {
        val first = EditSession.start(target, at)
        val changed = HumanEditSessions.adopt(target, listOf(first), trace(300), at.plusSeconds(301))
        assertThat(changed.count { it.isOpen }).isEqualTo(1)
        assertThat(changed.single { it.id == first.id }.period.endedAt).isEqualTo(at)
        assertThat(EditStatistics.of(close(changed)).human.milliseconds).isZero()
        assertThat(EditStatistics.of(close(changed)).human.count).isEqualTo(2)
    }

    private fun trace(vararg seconds: Long): InputTrace =
        InputTrace(
            requireNotNull(target.tabId),
            UUID.randomUUID(),
            seconds.map { InputInterval(at.plusSeconds(it), at.plusSeconds(it)) },
        )

    private fun close(sessions: List<EditSession>): List<EditSession> =
        sessions.map { it.close(it.period.lastActivityAt, EditEndReason.HUMAN) }
}
