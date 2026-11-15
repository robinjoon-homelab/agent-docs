package xyz.robinjoon.agentdocs.webhook.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.CallerAuthentication
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.support.web.problemFields
import xyz.robinjoon.agentdocs.webhook.application.model.EndpointRegistration
import xyz.robinjoon.agentdocs.webhook.application.model.IssuedEndpoint
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookOperator
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointNotFoundException
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointUseCase
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookOwnerOnlyException
import xyz.robinjoon.agentdocs.webhook.domain.InvalidWebhookException
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventActor
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventFacts
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventSubject
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDeliveryId
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookEvent
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.EndpointStoppedException
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.Subscription
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookUrl
import java.time.Instant
import java.util.UUID

class WebhookApiDocumentationTest : RestDocsWebTest() {
    private val webhooks = mockk<WebhookEndpointUseCase>()
    private val mockMvc = restDocsMockMvc(WebhookController(webhooks), WebhookExceptionHandler())
    private val userId = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val owner = CallerAuthentication(Caller(userId, "robinjoon", true, null, null, CallerScope.OWNER))
    private val operator = WebhookOperator(userId, "robinjoon", owner = true, agent = false)
    private val at = Instant.parse("2026-11-16T03:00:00Z")
    private val secret = WebhookSecret.parse("whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw")
    private val endpoint =
        WebhookEndpoint.register(
            WebhookEndpointId(UUID.fromString("44444444-4444-4444-8444-444444444444")),
            Subscription.of(
                WebhookUrl.of("http://192.168.0.20:8080/agent-docs"),
                setOf(WebhookEventType.DOCUMENT_CHANGED, WebhookEventType.COMMENT_MENTIONED),
            ),
            secret,
            at,
        )

    @Test
    fun `the owner lists endpoints without their secrets`() {
        every { webhooks.list(operator) } returns listOf(endpoint)

        mockMvc
            .perform(get("/api/webhooks").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].events[0]").value("comment.mentioned"))
            .andExpect(jsonPath("$[0].secret").doesNotExist())
            .andDo(documented("webhooks-list", responseFields(*endpointFields("[]."))))
    }

    @Test
    fun `registering shows the signing secret once`() {
        val registration = EndpointRegistration("http://192.168.0.20:8080/agent-docs", setOf("document.changed", "comment.mentioned"))
        every { webhooks.register(operator, registration) } returns IssuedEndpoint(endpoint)

        mockMvc
            .perform(
                post("/api/webhooks")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"http://192.168.0.20:8080/agent-docs","events":["document.changed","comment.mentioned"]}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.secret").value(secret.serialized))
            .andDo(
                documented(
                    "webhooks-register",
                    requestFields(
                        fieldWithPath("url").description("받을 주소. http·https만, 사용자 정보와 #fragment 없이. 홈랩 사설 주소도 된다"),
                        fieldWithPath("events").description("document.changed, comment.created, comment.mentioned 중 하나 이상"),
                    ),
                    responseFields(*endpointFields("endpoint."), fieldWithPath("secret").description(SECRET)),
                ),
            )
    }

    @Test
    fun `stopping keeps the endpoint and its records`() {
        every { webhooks.stop(operator, endpoint.id) } returns endpoint.stop(StopReason.OWNER, at.plusSeconds(60))

        mockMvc
            .perform(post("/api/webhooks/{id}/stop", endpoint.id.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stopReason").value("owner"))
            .andDo(
                documented(
                    "webhooks-stop",
                    pathParameters(parameterWithName("id").description("엔드포인트 ID")),
                    responseFields(*endpointFields("")),
                ),
            )
    }

    @Test
    fun `replacing the secret shows the new one once`() {
        val replaced = endpoint.rotateSecret(WebhookSecret.of(ByteArray(32) { 1 }), at.plusSeconds(30))
        every { webhooks.rotateSecret(operator, endpoint.id) } returns IssuedEndpoint(replaced)

        mockMvc
            .perform(post("/api/webhooks/{id}/secret", endpoint.id.value).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.secret").value(replaced.secret.serialized))
            .andDo(
                documented(
                    "webhooks-rotate-secret",
                    responseFields(*endpointFields("endpoint."), fieldWithPath("secret").description(SECRET)),
                ),
            )
    }

    @Test
    fun `recent deliveries show their state, attempts, last failure, and next attempt`() {
        val subject = EventSubject(UUID.randomUUID(), UUID.randomUUID(), null, null, 4)
        val facts = EventFacts(42, at, subject, EventActor("human", "robinjoon", null))
        val event = WebhookEvent.of(WebhookEventType.DOCUMENT_CHANGED, facts, null, "https://docs.example.com")
        val id = WebhookDeliveryId(UUID.fromString("55555555-5555-4555-8555-555555555555"))
        val planned = WebhookDelivery.plan(id, endpoint.id, event, at)
        val failed = requireNotNull(planned.claim(at).record(1, AttemptResult.Answered(503, null), at.plusSeconds(1)))
        every { webhooks.deliveries(operator, endpoint.id, 20) } returns listOf(failed)

        mockMvc
            .perform(get("/api/webhooks/{id}/deliveries", endpoint.id.value).param("limit", "20").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].webhookId").value("msg_55555555555545558555555555555555"))
            .andExpect(jsonPath("$[0].nextAttemptAt").value("2026-11-16T03:00:06Z"))
            .andDo(
                documented(
                    "webhooks-deliveries",
                    queryParameters(parameterWithName("limit").optional().description("최대 개수. 기본 50, 1~100")),
                    responseFields(*deliveryFields()),
                ),
            )
    }

    @Test
    fun `agents and members are refused, bad input is invalid, and a stopped endpoint keeps its secret`() {
        val agent =
            CallerAuthentication(Caller(userId, "robinjoon", false, CallerAgent(UUID.randomUUID(), "Claude Code"), null, CallerScope.EDIT))
        val agentOperator = WebhookOperator(userId, "robinjoon", owner = false, agent = true)
        every { webhooks.list(agentOperator) } throws WebhookOwnerOnlyException("listing webhooks")
        every { webhooks.register(operator, any()) } throws InvalidWebhookException("url must not carry a user name or password")
        every { webhooks.rotateSecret(operator, endpoint.id) } throws EndpointStoppedException(endpoint.id)
        every { webhooks.stop(operator, WebhookEndpointId(userId)) } throws WebhookEndpointNotFoundException(WebhookEndpointId(userId))

        mockMvc
            .perform(get("/api/webhooks").principal(agent))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("forbidden"))
            .andDo(documented("webhooks-forbidden", responseFields(*problemFields(), *codeFields())))
        mockMvc
            .perform(
                post("/api/webhooks")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"https://u:p@x","events":[]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("invalid"))
        mockMvc
            .perform(post("/api/webhooks/{id}/secret", endpoint.id.value).principal(owner))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("endpoint_stopped"))
        mockMvc.perform(post("/api/webhooks/{id}/stop", userId).principal(owner)).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/webhooks")).andExpect(status().isUnauthorized)
    }

    private fun endpointFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("엔드포인트 ID"),
            fieldWithPath("${prefix}url").description("받는 주소"),
            fieldWithPath("${prefix}events").description("받는 이벤트"),
            fieldWithPath("${prefix}active").description("보내는 중인지. 중지하면 다시 켜지 않고 새로 등록한다"),
            fieldWithPath("${prefix}createdAt").description("등록 시각. 이 시각 이후의 이벤트만 보낸다"),
            fieldWithPath("${prefix}secretRotatedAt").type(JsonFieldType.STRING).optional().description("서명 비밀을 마지막으로 바꾼 시각"),
            fieldWithPath("${prefix}stoppedAt").type(JsonFieldType.STRING).optional().description("중지한 시각"),
            fieldWithPath("${prefix}stopReason").type(JsonFieldType.STRING).optional().description("owner(소유자가 중지) 또는 gone(수신처가 410)"),
        )

    private fun deliveryFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("[].id").description("전달 ID"),
            fieldWithPath("[].webhookId").description("webhook-id 헤더. 재시도에도 같다"),
            fieldWithPath("[].event").description("이벤트 종류"),
            fieldWithPath("[].activityId").description("원천 활동 로그 ID"),
            fieldWithPath("[].mentioned").type(JsonFieldType.STRING).optional().description("comment.mentioned의 멘션된 로그인"),
            fieldWithPath("[].occurredAt").description("이벤트 시각"),
            fieldWithPath("[].state").description("pending, succeeded, failed"),
            fieldWithPath("[].attempts").description("시도한 횟수"),
            fieldWithPath("[].maxAttempts").description("최대 시도 횟수(8)"),
            fieldWithPath("[].nextAttemptAt").type(JsonFieldType.STRING).optional().description("다음 시도 시각. 끝난 전달은 없다"),
            fieldWithPath("[].lastAttemptAt").type(JsonFieldType.STRING).optional().description("마지막 시도 시각"),
            fieldWithPath("[].lastStatus").type(JsonFieldType.NUMBER).optional().description("마지막 응답 상태 코드"),
            fieldWithPath("[].lastError").type(JsonFieldType.STRING).optional().description("응답이 없을 때의 짧은 오류 코드(timeout 등)"),
        )

    private fun codeFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("code").description("forbidden, invalid, not_found, endpoint_stopped, unauthenticated"),
            fieldWithPath("fix").type(JsonFieldType.STRING).optional().description("고치는 법"),
        )

    private companion object {
        const val SECRET = "Standard Webhooks 서명 비밀(whsec_). 이 응답에서만 보여 준다"
    }
}
