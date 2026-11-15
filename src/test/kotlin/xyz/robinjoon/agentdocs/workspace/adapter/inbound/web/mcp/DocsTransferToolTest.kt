package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentTransfer
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceReference
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentTransferUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath

class DocsTransferToolTest {
    private val transfers = mockk<DocumentTransferUseCase>()
    private val statistics = mockk<EditStatisticsRecorder>()
    private val tool = DocsTransferTool(transfers, statistics)
    private val json = JsonMapper.builder().build()

    @Test
    fun `transfer tool returns the same captured location as rest`() {
        val context = WebFixtures.webContext
        val doc = WebFixtures.document(revision = 4)
        val request = DocumentTransfer(WebFixtures.space.slug, DocumentPath("moved"), 4)
        val moved = doc.transfer(doc.spaceId, request.path, WebFixtures.at)
        every { transfers.transfer(context, doc.id, request) } returns
            DocumentWrite(
                moved,
                5,
                emptyList(),
                false,
                false,
                space = SpaceReference(WebFixtures.space.id, WebFixtures.space.slug, WebFixtures.space.profile.name),
            )
        every { statistics.agent(context.access, doc.id) } returns true
        val input = """{"document":"${doc.id}","space":"${WebFixtures.space.slug}","path":"moved","baseVersion":4}"""
        val result =
            tool.call(
                Arguments(json.readTree(input)),
                context,
            )
        assertThat(result.data?.get("path")).isEqualTo("moved")
        assertThat(result.data?.get("version")).isEqualTo(5)
        assertThat(tool.definition.name).isEqualTo("docs_transfer")
    }

    @Test
    fun `transfer tool rejects a missing baseVersion`() {
        val input = """{"document":"${WebFixtures.documentId}","space":"team","path":"moved"}"""
        assertThatThrownBy {
            tool.call(
                Arguments(json.readTree(input)),
                WebFixtures.webContext,
            )
        }.isInstanceOf(InvalidRequestException::class.java)
    }
}
