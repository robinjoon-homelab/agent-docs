package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented

class AdocsClientMetadataApiDocumentationTest : RestDocsWebTest() {
    private val mvc =
        restDocsMockMvc(
            AdocsClientMetadataController(AccessSettings("owner", emptySet(), "https://docs.example")),
            AccessExceptionHandler(),
        )

    @Test
    fun `public metadata describes the first party PKCE client`() {
        mvc
            .perform(get("/oauth/clients/adocs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.client_id").value("https://docs.example/oauth/clients/adocs"))
            .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"))
            .andExpect(jsonPath("$.redirect_uris[0]").value("http://127.0.0.1/callback"))
            .andDo(
                documented(
                    "adocs-client-metadata",
                    responseFields(
                        fieldWithPath("client_id").description("공개 HTTPS issuer의 /oauth/clients/adocs. 서버 자신의 정확한 ID만 로컬 메타데이터로 판정"),
                        fieldWithPath("client_name").description("adocs"),
                        fieldWithPath("redirect_uris").description("127.0.0.1 loopback callback. 기존 동적 포트 검증 유지"),
                        fieldWithPath("token_endpoint_auth_method").description("none. 비밀 없는 공개 클라이언트, PKCE S256 필요"),
                        fieldWithPath("grant_types").description("authorization_code, refresh_token. 기존 사용자 승인·scope·resource 검증 유지"),
                        fieldWithPath("response_types").description("code"),
                    ),
                ),
            )
    }
}
