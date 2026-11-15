package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import java.util.UUID

class DocumentAssetApiDocumentationTest : RestDocsWebTest() {
    private val assets = mockk<DocumentAssetUseCase>()
    private val mvc = restDocsMockMvc(DocumentAssetController(assets), WorkspaceExceptionHandler())
    private val reference = AssetId(UUID.randomUUID())
    private val bytes = "<html>preview</html>".toByteArray()
    private val copy =
        Asset.upload(
            AssetId(UUID.randomUUID()),
            WebFixtures.spaceId,
            AssetContent.of(AssetMediaType.HTML, bytes),
            AssetOrigin("preview.html", null, ownerAccess.actor, WebFixtures.at),
        )

    @Test
    fun `document attachment download remains sandboxed and does not cache a mutable access boundary`() {
        every { assets.content(ownerAccess, documentId, reference) } returns AssetFile(copy, bytes)
        mvc
            .perform(get("/api/documents/{id}/assets/{assetId}/content", documentId, reference.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(content().bytes(bytes))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", "attachment"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andDo(
                documented(
                    "document-assets-content",
                    pathParameters(
                        parameterWithName("id").description("현재 문서 ID"),
                        parameterWithName("assetId").description("불변 본문·댓글에 적힌 참조 첨부 ID"),
                    ),
                ),
            )
    }

    @Test
    fun `html source retains the logical reference id rather than exposing the copy identity`() {
        every { assets.content(ownerAccess, documentId, reference) } returns AssetFile(copy, bytes)
        mvc
            .perform(get("/api/documents/{id}/assets/{assetId}/source", documentId, reference.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.assetId").value(reference.value.toString()))
            .andExpect(jsonPath("$.source").value(bytes.toString(Charsets.UTF_8)))
            .andDo(
                documented(
                    "document-assets-source",
                    pathParameters(
                        parameterWithName("id").description("현재 문서 ID"),
                        parameterWithName("assetId").description("문서의 참조 첨부 ID"),
                    ),
                    responseFields(
                        fieldWithPath("assetId").description("원래 참조 ID"),
                        fieldWithPath("source").description("바인딩된 목적 HTML의 불변 원문"),
                    ),
                ),
            )
    }

    @Test
    fun `an arbitrary logical reference is not found`() {
        every { assets.content(ownerAccess, documentId, reference) } throws AssetNotFoundException(reference)
        mvc
            .perform(
                get(
                    "/api/documents/{id}/assets/{assetId}/content",
                    documentId,
                    reference.value,
                ).principal(owner),
            ).andExpect(status().isNotFound)
    }
}
