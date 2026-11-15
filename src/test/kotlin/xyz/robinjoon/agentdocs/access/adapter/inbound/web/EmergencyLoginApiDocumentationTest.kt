package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.request.RequestDocumentation.formParameters
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.port.input.EmergencyLoginUseCase
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented

class EmergencyLoginApiDocumentationTest : RestDocsWebTest() {
    private val emergency = mockk<EmergencyLoginUseCase>()
    private val mvc =
        restDocsMockMvc(
            EmergencyLoginController(emergency, AccessSettings("owner", emptySet(), "https://docs.example")),
            AccessExceptionHandler(),
        )

    @Test
    fun `GET only presents confirmation and POST starts a normal secure browser session`() {
        mvc
            .perform(get("/emergency-login"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("로그인")))
            .andDo(documented("emergency-login-confirm"))
        every { emergency.consume("fake-token") } returns "adt_w_fake"
        mvc
            .perform(post("/emergency-login").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", "fake-token"))
            .andExpect(status().isNoContent)
            .andExpect(cookie().value("agent_docs_session", "adt_w_fake"))
            .andExpect(cookie().httpOnly("agent_docs_session", true))
            .andExpect(cookie().secure("agent_docs_session", true))
            .andExpect(cookie().maxAge("agent_docs_session", 2592000))
            .andDo(documented("emergency-login-consume", formParameters(parameterWithName("token").description("10분 안에 한 번만 소비하는 비상 토큰"))))
    }

    @Test
    fun `invalid grants return a generic failure without a session cookie`() {
        every { emergency.consume("fake-token") } throws IllegalArgumentException("Emergency login is unavailable")
        mvc
            .perform(post("/emergency-login").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", "fake-token"))
            .andExpect(status().isBadRequest)
            .andExpect(cookie().doesNotExist("agent_docs_session"))
            .andDo(documented("emergency-login-unavailable"))
        mvc.perform(post("/emergency-login")).andExpect(status().isBadRequest)
    }
}
