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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.support.web.problemFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAsset
import xyz.robinjoon.agentdocs.workspace.application.model.FolderImportResult
import xyz.robinjoon.agentdocs.workspace.application.model.ImportedFile
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ExportIncompleteException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ExportUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.FolderImportUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

class PortabilityApiDocumentationTest : RestDocsWebTest() {
    private val imports = mockk<FolderImportUseCase>()
    private val exports = mockk<ExportUseCase>()
    private val mvc = restDocsMockMvc(PortabilityController(imports, exports), WorkspaceExceptionHandler())

    @Test
    fun `missing originals return an incomplete problem instead of a partial zip`() {
        val asset = ExportAsset("77777777-7777-4777-8777-777777777777", "html", "a".repeat(64))
        val failure = ExportIncompleteException(asset, AssetContentMissingException(asset.sha256))
        every { exports.export(ownerAccess, null) } throws failure
        every { exports.export(ownerAccess, SpaceSlug("docs")) } throws failure

        listOf("workspace" to "/api/exports/workspace.zip", "space" to "/api/spaces/docs/export.zip").forEach { (scope, path) ->
            mvc
                .perform(get(path).principal(owner))
                .andExpect(status().isInternalServerError)
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.code").value("export_incomplete"))
                .andExpect(jsonPath("$.detail").value("export could not include all attachment originals"))
                .andExpect(jsonPath("$.fix").value(failure.fix))
                .andExpect(jsonPath("$.failedAsset.id").value(asset.id))
                .andExpect(jsonPath("$.failedAsset.sha256").value(asset.sha256))
                .andExpect(jsonPath("$.failedAsset.extension").doesNotExist())
                .andExpect(jsonPath("$.scanComplete").value(false))
                .andDo(
                    documented(
                        "portability-export-incomplete-$scope",
                        responseFields(
                            *problemFields(),
                            fieldWithPath("code").description("export_incomplete. 완전한 ZIP은 반환하지 않음"),
                            fieldWithPath("fix").description("검증된 원본 복구 후 재시도. 첨부 정보와 문서 이력 보존"),
                            fieldWithPath("failedAsset").description("처음 실패한 첨부. 사람 소유자만 확인 가능"),
                            fieldWithPath("failedAsset.id").description("저장 첨부 ID"),
                            fieldWithPath("failedAsset.sha256").description("누락 원본의 내용 해시"),
                            fieldWithPath("scanComplete").description("false. 추가 누락이 없음을 보장하지 않음"),
                        ),
                    ),
                )
        }
    }

    @Test
    fun `missing spaces and denied agents keep their errors without attachment context`() {
        every { exports.export(ownerAccess, SpaceSlug("missing")) } throws SpaceNotFoundException("missing")
        every { exports.export(agentAccess, SpaceSlug("docs")) } throws
            PermissionDeniedException(Permission.OWNER, "exporting workspace data")
        mvc
            .perform(get("/api/spaces/missing/export.zip").principal(owner))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("not_found"))
            .andExpect(jsonPath("$.failedAsset").doesNotExist())
        mvc
            .perform(get("/api/spaces/docs/export.zip").principal(agent))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("forbidden"))
            .andExpect(jsonPath("$.failedAsset").doesNotExist())
    }

    @Test
    fun `ordinary missing asset content keeps the existing not found mapping`() {
        val assets = mockk<AssetUseCase>()
        val assetId = AssetId(UUID.fromString("88888888-8888-4888-8888-888888888888"))
        val assetMvc = restDocsMockMvc(AssetController(assets, mockk()), WorkspaceExceptionHandler())
        every { assets.content(ownerAccess, assetId) } throws AssetContentMissingException("missing-hash")
        assetMvc
            .perform(get("/api/assets/${assetId.value}/content").principal(owner))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("not_found"))
            .andExpect(jsonPath("$.failedAsset").doesNotExist())
    }

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
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=agent-docs.zip"))
            .andDo(documented("portability-export-workspace"))
        mvc
            .perform(get("/api/spaces/{slug}/export.zip", "docs").principal(owner))
            .andExpect(status().isOk)
            .andDo(documented("portability-export-space", pathParameters(parameterWithName("slug").description("보관 공간 포함 대상 스페이스"))))
    }
}
