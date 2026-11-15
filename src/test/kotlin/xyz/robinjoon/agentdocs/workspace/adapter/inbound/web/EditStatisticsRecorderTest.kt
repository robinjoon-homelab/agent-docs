package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import java.util.UUID

class EditStatisticsRecorderTest {
    private val sessions = mockk<EditSessionUseCase>()
    private val recorder = EditStatisticsRecorder(sessions)
    private val document = WorkspaceFixtures.document(emptyList(), 5)
    private val written = DocumentWrite(document, document.revision, emptyList(), false, false)
    private val context = WorkspaceFixtures.context(WorkspaceFixtures.ownerAccess)

    @Test
    fun `a statistics storage failure does not replace the committed document result`() {
        every { sessions.recordHuman(any(), any()) } throws DataAccessResourceFailureException("unavailable")
        val trace =
            """{"inputTrace":{"tabId":"11111111-1111-4111-8111-111111111111"""" +
                ""","batchId":"22222222-2222-4222-8222-222222222222"""" +
                ""","intervals":[{"firstInputAt":"2026-10-18T09:00:00Z","lastInputAt":"2026-10-18T09:00:00Z"}]}}"""
        val recorded = recorder.human(context, written, JsonMapper.builder().build().readTree(trace))
        assertThat(recorded).isFalse()
        assertThat(written.document.version).isEqualTo(5)
        assertThat(written.document).isEqualTo(document)
    }

    @Test
    fun `invalid optional trace cannot turn a successful document write into an error`() {
        val body = JsonMapper.builder().build().readTree("""{"inputTrace":{"tabId":"invalid"}}""")
        assertThat(recorder.human(context, written, body)).isFalse()
        assertThat(written.document.version).isEqualTo(5)
    }

    @Test
    fun `interrupted optional work preserves the interrupt and aborts rather than swallowing it`() {
        val failure = RuntimeException(InterruptedException("cancelled"))
        try {
            assertThatThrownBy { EditStatisticsExceptionHandler.afterWrite { throw failure } }.isSameAs(failure)
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `new multiple-foreground envelope is accepted by the recorder`() {
        every { sessions.recordHuman(any(), any()) } returns emptyList()
        assertThat(recorder.human(context, written, envelope())).isTrue()
        assertThat(written.document.version).isEqualTo(5)
    }

    @Test
    fun `multiple-foreground storage failure remains separate from the committed document`() {
        every { sessions.recordHuman(any(), any()) } throws DataAccessResourceFailureException("unavailable")
        assertThat(recorder.human(context, written, envelope())).isFalse()
        assertThat(written.document).isEqualTo(document)
    }

    @Test
    fun `no-op ignores all foreground traces and malformed children cannot fail document saving`() {
        val bad = JsonMapper.builder().build().readTree("""{"inputTrace":{"batchId":"invalid","traces":[{}]}}""")
        assertThat(recorder.human(context, written.copy(unchanged = true), bad)).isTrue()
        assertThat(recorder.human(context, written, bad)).isFalse()
        assertThat(written.document.version).isEqualTo(5)
    }

    private fun envelope(): JsonNode {
        val timestamp = WorkspaceFixtures.now.toString()
        val interval = mapOf("firstInputAt" to timestamp, "lastInputAt" to timestamp)
        val children =
            listOf(UUID.randomUUID(), UUID.randomUUID()).map { tab ->
                mapOf("tabId" to tab.toString(), "batchId" to UUID.randomUUID().toString(), "intervals" to listOf(interval))
            }
        val trace = mapOf("batchId" to UUID.randomUUID().toString(), "traces" to children)
        return JsonMapper.builder().build().valueToTree<JsonNode>(mapOf("inputTrace" to trace))
    }
}
