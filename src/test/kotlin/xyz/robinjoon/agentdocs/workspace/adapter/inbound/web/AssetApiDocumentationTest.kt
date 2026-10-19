package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.partWithName
import org.springframework.restdocs.request.RequestDocumentation.requestParts
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.webContext
import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

class AssetApiDocumentationTest : RestDocsWebTest() {
    private val assets = mockk<AssetUseCase>()
    private val mockMvc = restDocsMockMvc(AssetController(assets), WorkspaceExceptionHandler())
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val assetId = AssetId(UUID.fromString("88888888-8888-4888-8888-888888888888"))
    private val asset =
        Asset.upload(
            assetId,
            spaceId,
            AssetContent.of(AssetMediaType.PNG, png),
            AssetOrigin("flow.png", null, ownerAccess.actor, at),
        )

    @Test
    fun `an upload returns the asset and where its bytes are served`() {
        every { assets.upload(webContext, SpaceSlug("agent-docs"), any()) } returns asset

        mockMvc
            .perform(
                multipart(
                    "/api/spaces/{slug}/assets",
                    "agent-docs",
                ).file(MockMultipartFile("file", "flow.png", "image/png", png)).principal(owner),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/assets/$assetId/content"))
            .andDo(
                documented(
                    "assets-upload",
                    requestParts(
                        partWithName("file").description("PNG, JPEG, GIF, WebP, SVG, 20MB 이하. 종류는 파일 내용으로 판단하고 SVG는 스크립트를 지운 뒤 저장한다"),
                    ),
                    responseHeaders(headerWithName("Location").description("파일 내용 경로")),
                    responseFields(*assetFields()),
                ),
            )
    }

    @Test
    fun `an image address is downloaded and keeps its source`() {
        val imported =
            Asset.upload(
                assetId,
                spaceId,
                AssetContent.of(AssetMediaType.PNG, png),
                AssetOrigin("flow.png", "https://example.com/flow.png", ownerAccess.actor, at),
            )
        every { assets.import(webContext, SpaceSlug("agent-docs"), "https://example.com/flow.png") } returns imported

        mockMvc
            .perform(
                post("/api/spaces/{slug}/assets/import", "agent-docs")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"https://example.com/flow.png"}"""),
            ).andExpect(status().isCreated)
            .andDo(
                documented(
                    "assets-import",
                    requestFields(fieldWithPath("url").description("공개 http(s) 이미지 주소. 홈랩 내부 주소는 받지 않는다")),
                    responseFields(*assetFields()),
                ),
            )
    }

    @Test
    fun `content is served immutable with a sandbox policy`() {
        every { assets.content(ownerAccess, assetId) } returns AssetFile(asset, png)

        mockMvc
            .perform(get("/api/assets/{id}/content", assetId.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(content().bytes(png))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andDo(
                documented(
                    "assets-content",
                    responseHeaders(
                        headerWithName("Content-Type").description("파일 종류"),
                        headerWithName("Content-Security-Policy").description("sandbox. SVG나 잘못 표시된 파일의 스크립트가 실행되지 않는다"),
                        headerWithName("Cache-Control").description("파일은 바뀌지 않으므로 1년 동안 캐시한다"),
                        headerWithName("X-Content-Type-Options").description("nosniff"),
                    ),
                ),
            )
    }

    @Test
    fun `a file that is not a supported image is refused`() {
        every { assets.upload(webContext, SpaceSlug("agent-docs"), any()) } throws
            InvalidAssetException("only PNG, JPEG, GIF, WebP, and SVG images can be uploaded")

        mockMvc
            .perform(
                multipart(
                    "/api/spaces/{slug}/assets",
                    "agent-docs",
                ).file(MockMultipartFile("file", "notes.pdf", "application/pdf", "%PDF".toByteArray())).principal(owner),
            ).andExpect(status().isBadRequest)
            .andDo(documented("assets-invalid", responseFields(*workspaceProblemFields())))
    }

    private fun assetFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("id").description("첨부 ID. 이미지 블록과 ![alt](asset:ID)가 이 ID를 쓴다"),
            fieldWithPath("mediaType").description("파일 종류"),
            fieldWithPath("size").description("바이트 수"),
            fieldWithPath("fileName").description("원래 파일 이름"),
            fieldWithPath("sourceUrl").type(JsonFieldType.STRING).optional().description("주소로 가져왔을 때 원래 주소"),
            fieldWithPath("contentUrl").description("내용 경로"),
        )
}
