package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditEndReason
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSession
import xyz.robinjoon.agentdocs.workspace.domain.editsession.EditSessionTarget

class EditStatisticsToolsTest {
    private val sessions = mockk<EditSessionUseCase>()
    private val access = WorkspaceFixtures.agentAccess(Permission.EDIT, setOf(WorkspaceFixtures.spaceId))
    private val context = WorkspaceFixtures.context(access, ChangeSource.MCP, "op")
    private val target = EditSessionTarget(WorkspaceFixtures.documentId, WorkspaceFixtures.spaceId, WorkspaceFixtures.agent, null)
    private val session = EditSession.start(target, WorkspaceFixtures.now)
    private val json = JsonMapper.builder().build()

    @Test
    fun `start returns a statistics session and requires an idempotency key`() {
        every { sessions.start(context, target.documentId) } returns session
        val tool = StatsEditStartTool(sessions)
        val args = Arguments(json.readTree("""{"document":"${target.documentId.value}","opId":"op"}"""))
        val result = tool.call(args, context)
        assertThat(result.isError).isFalse()
        assertThat(result.data).containsEntry("id", session.id).containsEntry("endedAt", null)
        assertThat(tool.definition.required).containsExactly("document", "opId")
    }

    @Test
    fun `end exposes an estimated result without relabeling it as explicit`() {
        val ended = session.close(WorkspaceFixtures.now, EditEndReason.ESTIMATED)
        every { sessions.end(context, session.id) } returns ended
        val result = StatsEditEndTool(sessions).call(Arguments(json.readTree("""{"session":"${session.id}","opId":"op"}""")), context)
        assertThat(result.data).containsEntry("reason", "estimated").containsEntry("endedAt", WorkspaceFixtures.now)
        assertThat(result.isError).isFalse()
    }
}
