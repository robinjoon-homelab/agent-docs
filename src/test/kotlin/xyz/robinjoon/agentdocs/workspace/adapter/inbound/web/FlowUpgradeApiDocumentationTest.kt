package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyFlowUpgrade

class FlowUpgradeApiDocumentationTest : RestDocsWebTest() {
    private val documents = mockk<DocumentUseCase>()
    private val mvc = restDocsMockMvc(FlowUpgradeController(documents, mockk(relaxed = true)), WorkspaceExceptionHandler())

    @Test
    fun `legacy document upgrade returns fresh version and preserved block ids`() {
        val source = WebFixtures.document()
        val converted = LegacyFlowUpgrade.convert(FlowRules.initial("phone", "M"))
        val doc = Document.create(source.id, source.header.copy(type = "flow"), converted, WebFixtures.at)
        every { documents.upgradeFlow(any(), doc.id, 5) } returns DocumentWrite(doc, doc.revision, converted.map { it.id }, false, false)
        mvc
            .perform(
                post("/api/documents/{id}/flow-design/upgrade", doc.id)
                    .principal(WebFixtures.owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseVersion":5,"opId":"upgrade-flow"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.documentId").value(doc.id.toString()))
            .andExpect(jsonPath("$.blocks[0].id").value(converted.first().id.value))
            .andDo(
                documented(
                    "flow-design-upgrade",
                    requestFields(
                        fieldWithPath("baseVersion").description("읽을 때의 문서 버전"),
                        fieldWithPath("opId").description("재시도 키").optional(),
                    ),
                    relaxedResponseFields(
                        fieldWithPath("version").description("변환 결과의 문서 버전"),
                        fieldWithPath("unchanged").description("이미 변환된 문서인지 여부"),
                    ),
                ),
            )
    }

    @Test
    fun `upgrade requires an integer base version`() {
        mvc
            .perform(
                post("/api/documents/{id}/flow-design/upgrade", WebFixtures.documentId)
                    .principal(WebFixtures.owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseVersion":"1"}"""),
            ).andExpect(status().isBadRequest)
    }
}
