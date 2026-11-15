package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationPage
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.NavigationUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

class NavigationApiDocumentationTest : RestDocsWebTest() {
    private val navigation = mockk<NavigationUseCase>()
    private val mvc = restDocsMockMvc(NavigationController(navigation), WorkspaceExceptionHandler())
    private val id = DocumentId(UUID.randomUUID())

    @Test
    fun `personal lists have access filtered totals`() {
        every { navigation.recent(ownerAccess, 20) } returns NavigationPage(emptyList(), 0)
        every { navigation.favorites(ownerAccess, 20) } returns NavigationPage(emptyList(), 0)
        listOf("recent", "favorites").forEach { kind ->
            mvc
                .perform(get("/api/navigation/$kind").principal(owner))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.total").value(0))
                .andDo(
                    documented(
                        "navigation-$kind",
                        responseFields(
                            subsectionWithPath(
                                "documents",
                            ).description("현재 접근 가능한 휴지통 밖 문서. id, spaceId, spaceSlug, spaceName, path, title"),
                            fieldWithPath("total").description("권한 필터 후 전체 개수. limit 기본 20, 1~1000"),
                        ),
                    ),
                )
        }
    }

    @Test
    fun `explicit visit and favorite writes return no content`() {
        every { navigation.recordView(ownerAccess, id) } just Runs
        every { navigation.favorite(ownerAccess, id, true) } just Runs
        mvc
            .perform(post("/api/navigation/documents/{id}/view", id).principal(owner))
            .andExpect(status().isNoContent)
            .andDo(documented("navigation-view", pathParameters(parameterWithName("id").description("실제로 연 문서 ID"))))
        mvc
            .perform(
                put("/api/navigation/documents/{id}/favorite", id)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"enabled":true}"""),
            ).andExpect(status().isNoContent)
            .andDo(
                documented(
                    "navigation-favorite",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    requestFields(fieldWithPath("enabled").description("true 설정, false 해제")),
                ),
            )
    }

    @Test
    fun `backlinks expose only the filtered page and count`() {
        every { navigation.backlinks(ownerAccess, id, 20) } returns NavigationPage(emptyList(), 0)
        mvc
            .perform(get("/api/navigation/documents/{id}/backlinks", id).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
            .andDo(
                documented(
                    "navigation-backlinks",
                    pathParameters(parameterWithName("id").description("대상 문서 ID")),
                    responseFields(
                        subsectionWithPath("documents").description("대상과 출처의 현재 권한으로 필터한 문서"),
                        fieldWithPath("total").description("권한 필터 후 전체 개수"),
                    ),
                ),
            )
    }

    @Test
    fun `mobile target reads an exact path without title search`() {
        every { navigation.mobileTarget(ownerAccess, "notes", "일기/2026-11-16") } returns
            xyz.robinjoon.agentdocs.workspace.application.model
                .MobileMemoTarget(id, 7, emptyList())
        mvc
            .perform(get("/api/navigation/mobile-target").param("space", "notes").param("path", "일기/2026-11-16").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(7))
            .andDo(
                documented(
                    "navigation-mobile-target",
                    responseFields(
                        fieldWithPath("id").description("정확한 경로의 문서 ID"),
                        fieldWithPath("version").description("현재 내용 버전"),
                        subsectionWithPath("blocks").description("현재 블록. 기존 ID를 유지하고 차분 삽입한다"),
                    ),
                ),
            )
    }

    @Test
    fun `favorite status does not depend on list pagination`() {
        every { navigation.isFavorite(ownerAccess, id) } returns true
        mvc
            .perform(get("/api/navigation/documents/{id}/favorite", id).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andDo(documented("navigation-favorite-status", responseFields(fieldWithPath("enabled").description("현재 사용자의 즐겨찾기 여부"))))
    }

    @Test
    fun `invisible backlink target is not found`() {
        every { navigation.backlinks(ownerAccess, id, 20) } throws DocumentNotFoundException(id)
        mvc
            .perform(get("/api/navigation/documents/{id}/backlinks", id).principal(owner))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("not_found"))
            .andDo(documented("navigation-backlinks-not-found", responseFields(*workspaceProblemFields())))
    }
}
