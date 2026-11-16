package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.config.McpConfig
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsUploadTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpSettings
import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.model.AssetSource
import xyz.robinjoon.agentdocs.workspace.application.model.DrawingText
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedScene
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import java.util.UUID

// Reading a hand-drawn diagram: the document-scoped REST source for the web editor and the docs_upload read mode for
// agents. Use cases are mocked; the rules behind them belong to the service and verifier tests.
class DrawingApiDocumentationTest : RestDocsWebTest() {
    private val documentAssets = mockk<DocumentAssetUseCase>()
    private val assets = mockk<AssetUseCase>()
    private val rest = restDocsMockMvc(DocumentAssetController(documentAssets), WorkspaceExceptionHandler())
    private val config = McpConfig()
    private val json = JsonMapper.builder().build()
    private val mapper = config.mcpJsonMapper(json)
    private val transport =
        config.mcpTransport(mapper, McpSettings(setOf("https://docs.example.com"))).also {
            val tools = listOf(DocsUploadTool(assets, mockk(), "https://docs.example.com/api/uploads", documentAssets))
            config.mcpServer(it, tools, config.mcpToolSpecifications(json, SimpleMeterRegistry()), mapper)
        }
    private val mcp = restDocsMockMvc(config.mcpRoutes(transport))
    private val sceneText = """{"type":"excalidraw","version":2,"elements":[{"id":"label","type":"text","text":"배포"}]}"""
    private val svgText = """<svg xmlns="http://www.w3.org/2000/svg"><text>배포</text></svg>"""
    private val scene = asset(AssetMediaType.EXCALIDRAW, sceneText)
    private val svg = asset(AssetMediaType.SVG, svgText)
    private val texts = VerifiedScene(1, 0, listOf(DrawingText("label", "배포", "box")))

    private fun asset(
        type: AssetMediaType,
        text: String,
    ): Asset =
        Asset.upload(
            AssetId(UUID.randomUUID()),
            WebFixtures.spaceId,
            AssetContent.of(type, text.toByteArray()),
            AssetOrigin("draw.${type.extension}", null, ownerAccess.actor, WebFixtures.at),
        )

    @Test
    fun `the web editor reads a drawing scene through its document as the stored JSON with its texts`() {
        every { documentAssets.source(ownerAccess, documentId, scene.id) } returns AssetSource(scene, sceneText, texts)

        rest
            .perform(get("/api/documents/{id}/assets/{assetId}/source", documentId, scene.id.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mediaType").value("application/vnd.excalidraw+json"))
            .andExpect(jsonPath("$.source").value(sceneText))
            .andExpect(jsonPath("$.texts[0].elementId").value("label"))
            .andExpect(jsonPath("$.texts[0].text").value("배포"))
            .andExpect(jsonPath("$.texts[0].containerId").value("box"))
            .andDo(
                documented(
                    "drawing-scene-source",
                    pathParameters(
                        parameterWithName("id").description("그림이 든 문서 ID"),
                        parameterWithName("assetId").description("그림 블록이 가리키는 장면 첨부 ID"),
                    ),
                    responseFields(
                        fieldWithPath("assetId").description("요청한 참조 ID"),
                        fieldWithPath("mediaType").description("application/vnd.excalidraw+json"),
                        fieldWithPath("source").description("올린 그대로의 Excalidraw 장면 JSON"),
                        fieldWithPath("texts[].elementId").description("삭제되지 않은 텍스트 요소 ID"),
                        fieldWithPath("texts[].text").description("사람이 적은 그대로의 글자"),
                        fieldWithPath("texts[].containerId").description("글자가 들어 있는 도형 ID, 없으면 null").optional(),
                    ),
                ),
            )
    }

    @Test
    fun `the web editor reads a drawing SVG through its document as its cleaned text`() {
        every { documentAssets.source(ownerAccess, documentId, svg.id) } returns AssetSource(svg, svgText, null)

        rest
            .perform(get("/api/documents/{id}/assets/{assetId}/source", documentId, svg.id.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mediaType").value("image/svg+xml"))
            .andExpect(jsonPath("$.source").value(svgText))
            .andExpect(jsonPath("$.texts").doesNotExist())
    }

    @Test
    fun `an agent gets a scene's texts and size by default and the stored JSON only when it asks`() {
        every { documentAssets.source(agentAccess, documentId, scene.id) } returns AssetSource(scene, sceneText, texts)

        callUpload("""{"space":"agent-docs","document":"$documentId","assetId":"${scene.id}"}""")
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"texts\"")))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"elementCount\":1")))
            .andExpect(jsonPath("$.result.content[0].text", not(containsString("\"source\""))))
        callUpload("""{"space":"agent-docs","document":"$documentId","assetId":"${scene.id}","includeSource":true}""")
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].text", containsString("\"source\"")))
            .andExpect(jsonPath("$.result.content[0].text", containsString("excalidraw")))
    }

    @Test
    fun `an agent reads a drawing SVG with its document and is told to pass one when it does not`() {
        every { documentAssets.source(agentAccess, documentId, svg.id) } returns AssetSource(svg, svgText, null)
        every { assets.content(agentAccess, svg.id) } returns AssetFile(svg, svgText.toByteArray())

        callUpload("""{"space":"agent-docs","document":"$documentId","assetId":"${svg.id}"}""")
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].text", containsString("image/svg+xml")))
            .andExpect(jsonPath("$.result.content[0].text", containsString("<text>")))
        callUpload("""{"space":"agent-docs","assetId":"${svg.id}"}""")
            .andExpect(jsonPath("$.result.isError").value(true))
            .andExpect(jsonPath("$.result.content[0].text", containsString("pass document")))
    }

    private fun callUpload(arguments: String): ResultActions =
        mcp
            .perform(
                post("/mcp")
                    .principal(agent)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"docs_upload","arguments":$arguments}}"""),
            ).andExpect(status().isOk)
}
