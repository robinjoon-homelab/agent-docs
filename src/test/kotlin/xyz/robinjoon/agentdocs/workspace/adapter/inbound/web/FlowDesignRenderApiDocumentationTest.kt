package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.Arguments
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowDesignRenderTool
import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderResult
import xyz.robinjoon.agentdocs.workspace.application.model.RenderUnavailableException
import xyz.robinjoon.agentdocs.workspace.application.port.input.FlowDesignRenderUseCase

class FlowDesignRenderApiDocumentationTest : RestDocsWebTest() {
    private val renders = mockk<FlowDesignRenderUseCase>()
    private val mvc = restDocsMockMvc(FlowDesignRenderController(renders), WorkspaceExceptionHandler())
    private val id = WebFixtures.documentId
    private val route = "/api/documents/{id}/flow-design/render"
    private val diagnosis = mapOf("viewport" to mapOf("width" to 390, "height" to 844), "diagnostics" to emptyList<String>())
    private val payload =
        """{"screen":"screen01","state":"state001","target":{"owner":"home","node":"title"},"mode":"viewport","image":false}"""

    @Test
    fun theRenderEndpointReturnsMeasurementsAndDefaultsToNoImage() {
        every { renders.render(any(), id, any()) } returns DesignRenderResult(diagnosis, null)
        mvc
            .perform(post(route, id).principal(WebFixtures.owner).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.viewport.width").value(390))
            .andExpect(jsonPath("$.image").doesNotExist())
            .andDo(
                documented(
                    "flow-design-render",
                    requestFields(
                        fieldWithPath("screen").description("화면의 서버 블록 ID"),
                        fieldWithPath("state").description("상태의 서버 블록 ID. 기본 화면이면 생략"),
                        subsectionWithPath("target").description("선택 요소의 owner, node와 선택적 via"),
                        fieldWithPath("mode").description("viewport 또는 full"),
                        fieldWithPath("image").description("true이면 선택한 범위의 PNG 포함. 기본값 false"),
                    ),
                ),
            )
    }

    @Test
    fun browserUnavailableIsRetryableAndRequestedMcpImagesAreOutsideText() {
        every { renders.render(any(), id, any()) } throws RenderUnavailableException("No configured Chromium")
        mvc
            .perform(post(route, id).principal(WebFixtures.owner).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isServiceUnavailable)
        every { renders.render(any(), id, any()) } returns DesignRenderResult(diagnosis, "iVBORw0KGgo=")
        val json = JsonMapper.builder().build()
        val args = Arguments(json.readTree("""{"document":"$id","screen":"screen01","image":true}"""))
        val result = FlowDesignRenderTool(renders).call(args, WebFixtures.webContext)
        assertThat(result.png).isEqualTo("iVBORw0KGgo=")
        assertThat(result.data).doesNotContainKey("image")
    }
}
