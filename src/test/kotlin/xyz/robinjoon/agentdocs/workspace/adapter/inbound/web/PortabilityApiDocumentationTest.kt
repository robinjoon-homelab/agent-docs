package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.multipart
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.partWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.requestParts
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.FolderImportResult
import xyz.robinjoon.agentdocs.workspace.application.model.ImportedFile
import xyz.robinjoon.agentdocs.workspace.application.port.input.ExportUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.FolderImportUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class PortabilityApiDocumentationTest : RestDocsWebTest() {
    private val imports = mockk<FolderImportUseCase>()
    private val exports = mockk<ExportUseCase>()
    private val mvc = restDocsMockMvc(PortabilityController(imports, exports), WorkspaceExceptionHandler())

    @Test
    fun `folder upload reports partial results using relative paths`() {
        every { imports.importFolder(any(), SpaceSlug("docs"), "Obsidian", any()) } returns
            FolderImportResult(listOf(ImportedFile("a.md", "failed", code = "missing_image")))
        mvc
            .perform(
                multipart("/api/spaces/{slug}/imports/folder", "docs")
                    .file(MockMultipartFile("files", "a.md", "text/markdown", "![x](missing.png)".toByteArray()))
                    .part(
                        org.springframework.mock.web
                            .MockPart("paths", "a.md".toByteArray()),
                    ).part(
                        org.springframework.mock.web
                            .MockPart("tool", "Obsidian".toByteArray()),
                    ).principal(owner),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.files[0].code").value("missing_image"))
            .andDo(
                documented(
                    "portability-import",
                    pathParameters(parameterWithName("slug").description("대상 스페이스")),
                    requestParts(
                        partWithName("files").description("paths 순서의 파일 원본. 반복 가능"),
                        partWithName("paths").description("선택한 폴더 기준 상대 경로. 반복 가능"),
                        partWithName("tool").description("첫 리비전 출처에 남길 원본 도구"),
                    ),
                    responseFields(
                        fieldWithPath("files").description("파일별 결과. 전체 폴더 원자성 없음"),
                        fieldWithPath("files[].path").description("상대 경로 또는 안전하지 않은 경로 표시"),
                        fieldWithPath("files[].status").description("created, failed, skipped"),
                        fieldWithPath("files[].documentId").optional().description("생성 문서 ID"),
                        fieldWithPath("files[].code").optional().description("실패 또는 건너뛰기 사유"),
                        fieldWithPath("files[].relatedPath").optional().description("누락 또는 지원하지 않는 이미지의 안전한 상대 경로"),
                    ),
                ),
            )
    }

    @Test
    fun `human owner downloads one zip for workspace or a space`() {
        every { exports.export(ownerAccess, null) } returns byteArrayOf(80, 75)
        every { exports.export(ownerAccess, SpaceSlug("docs")) } returns byteArrayOf(80, 75)
        mvc
            .perform(get("/api/exports/workspace.zip").principal(owner))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/zip"))
            .andExpect(content().bytes(byteArrayOf(80, 75)))
            .andDo(documented("portability-export-workspace"))
        mvc
            .perform(get("/api/spaces/{slug}/export.zip", "docs").principal(owner))
            .andExpect(status().isOk)
            .andDo(documented("portability-export-space", pathParameters(parameterWithName("slug").description("보관 공간 포함 대상 스페이스"))))
    }
}
