package xyz.robinjoon.agentdocs.webhook.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.webhook.application.model.EndpointRegistration
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookOperator
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointNotFoundException
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookOwnerOnlyException
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookSecretSource
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookEndpointService
import xyz.robinjoon.agentdocs.webhook.domain.InvalidWebhookException
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.EndpointStop
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

class WebhookEndpointServiceTest {
    private val endpoints = mockk<WebhookEndpointRepository>()
    private val deliveries = mockk<WebhookDeliveryRepository>()
    private val secrets = mockk<WebhookSecretSource>()
    private val activity = mockk<ActivityReporter>()
    private val now = WebhookFixtures.start
    private val service = WebhookEndpointService(endpoints, deliveries, secrets, activity, Clock.fixed(now, ZoneOffset.UTC))
    private val owner = WebhookOperator(UUID.randomUUID(), "robinjoon", owner = true, agent = false)
    private val replacement = WebhookSecret.of(ByteArray(32) { 9 })

    init {
        every { endpoints.add(any()) } just runs
        every { endpoints.update(any()) } just runs
        every { activity.record(any()) } just runs
        every { secrets.generate() } returns WebhookFixtures.secret
    }

    @Test
    fun `agents and members cannot manage webhooks, even the owner's own agent`() {
        val agent = WebhookOperator(owner.userId, "robinjoon", owner = false, agent = true)
        val ownerAgent = owner.copy(agent = true)
        val member = WebhookOperator(UUID.randomUUID(), "alice", owner = false, agent = false)
        val registration = EndpointRegistration("https://hooks.example.com", setOf("document.changed"))

        listOf(agent, ownerAgent, member).forEach { operator ->
            assertThatThrownBy { service.register(operator, registration) }.isInstanceOf(WebhookOwnerOnlyException::class.java)
            assertThatThrownBy { service.list(operator) }.isInstanceOf(WebhookOwnerOnlyException::class.java)
        }
    }

    @Test
    fun `registering returns the endpoint with its new secret, starting now`() {
        val registration = EndpointRegistration(" http://192.168.0.20:8080/hook ", setOf("comment.created", "comment.mentioned"))

        val issued = service.register(owner, registration)

        assertThat(issued.secret).isEqualTo(WebhookFixtures.secret.serialized)
        assertThat(
            issued.endpoint.subscription.url
                .toString(),
        ).isEqualTo("http://192.168.0.20:8080/hook")
        assertThat(issued.endpoint.subscription.events)
            .containsExactlyInAnyOrder(WebhookEventType.COMMENT_CREATED, WebhookEventType.COMMENT_MENTIONED)
        assertThat(issued.endpoint.timeline.createdAt).isEqualTo(now)
        assertThat(issued.toString()).doesNotContain(issued.secret)
    }

    @Test
    fun `an address with credentials or an unknown event is refused`() {
        assertThatThrownBy { service.register(owner, EndpointRegistration("https://u:p@hooks.example.com", setOf("document.changed"))) }
            .isInstanceOf(InvalidWebhookException::class.java)
        assertThatThrownBy { service.register(owner, EndpointRegistration("https://hooks.example.com", setOf("document.revised"))) }
            .isInstanceOf(InvalidWebhookException::class.java)
    }

    @Test
    fun `stopping keeps the endpoint as a record and stopping again changes nothing`() {
        val endpoint = WebhookFixtures.endpoint()
        every { endpoints.findByIdForUpdate(endpoint.id) } returns endpoint

        val stopped = service.stop(owner, endpoint.id)
        every { endpoints.findByIdForUpdate(endpoint.id) } returns stopped.stop(StopReason.GONE, now.plusSeconds(1))

        assertThat(stopped.timeline.stop).isEqualTo(EndpointStop(now, StopReason.OWNER))
        assertThat(service.stop(owner, endpoint.id).timeline.stop).isEqualTo(EndpointStop(now, StopReason.OWNER))
    }

    @Test
    fun `replacing the secret shows the new one once`() {
        val endpoint = WebhookFixtures.endpoint()
        every { endpoints.findByIdForUpdate(endpoint.id) } returns endpoint
        every { secrets.generate() } returns replacement

        val issued = service.rotateSecret(owner, endpoint.id)

        assertThat(issued.secret).isEqualTo(replacement.serialized)
        assertThat(issued.endpoint.timeline.secretRotatedAt).isEqualTo(now)
    }

    @Test
    fun `deliveries of an unknown endpoint are not found and the page size is bounded`() {
        val missing = WebhookEndpointId(UUID.randomUUID())
        val endpoint = WebhookFixtures.endpoint()
        every { endpoints.findById(missing) } returns null
        every { endpoints.findById(endpoint.id) } returns endpoint
        every { deliveries.recent(endpoint.id, 100) } returns emptyList()

        assertThatThrownBy { service.deliveries(owner, missing, 10) }.isInstanceOf(WebhookEndpointNotFoundException::class.java)
        assertThat(service.deliveries(owner, endpoint.id, 5_000)).isEmpty()
    }
}
