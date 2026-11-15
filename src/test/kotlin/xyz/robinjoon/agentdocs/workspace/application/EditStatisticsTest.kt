package xyz.robinjoon.agentdocs.workspace.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.EditStatistics
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditPeriod
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget
import java.util.UUID

class EditStatisticsTest {
    private val at = WorkspaceFixtures.now

    @Test
    fun `overlapping tabs count once per human while separate users remain additive`() {
        val other = Actor.human(UUID.randomUUID(), "other")
        val sessions = listOf(human(0, 20), human(10, 30), human(40, 40), human(0, 10, other))
        val result = EditStatistics.of(sessions)
        assertThat(result.human.milliseconds).isEqualTo(40_000)
        assertThat(result.human.count).isEqualTo(3)
    }

    @Test
    fun `open sessions are excluded and explicit and estimated agent time stay separate`() {
        val target = EditSessionTarget(WorkspaceFixtures.documentId, WorkspaceFixtures.spaceId, WorkspaceFixtures.agent, null)
        val explicit = EditSession.start(target, at).close(at.plusSeconds(10), EditEndReason.EXPLICIT)
        val estimated = EditSession.start(target, at).activity(at.plusSeconds(7)).close(at, EditEndReason.ESTIMATED)
        val result = EditStatistics.of(listOf(explicit, estimated, EditSession.start(target, at)))
        assertThat(result.agentExplicit.milliseconds).isEqualTo(10_000)
        assertThat(result.agentEstimated.milliseconds).isEqualTo(7_000)
        assertThat(result.openCount).isEqualTo(1)
    }

    private fun human(
        start: Long,
        end: Long,
        actor: Actor = WorkspaceFixtures.owner,
    ): EditSession {
        val target = EditSessionTarget(WorkspaceFixtures.documentId, WorkspaceFixtures.spaceId, actor, UUID.randomUUID())
        return EditSession.restore(
            UUID.randomUUID(),
            target,
            EditPeriod(at.plusSeconds(start), at.plusSeconds(end), at.plusSeconds(end), EditEndReason.HUMAN),
        )
    }
}
