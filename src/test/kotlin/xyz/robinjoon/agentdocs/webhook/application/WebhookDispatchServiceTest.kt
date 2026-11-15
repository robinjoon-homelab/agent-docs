package xyz.robinjoon.agentdocs.webhook.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookGateway
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookDeliveryLedger
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookDispatchService
import xyz.robinjoon.agentdocs.webhook.domain.WebhookEventType
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryAttempt
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryState
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventActor
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventFacts
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventSubject
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDeliveryId
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookEvent
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID

class WebhookDispatchServiceTest {
    private val endpoints = mockk<WebhookEndpointRepository>()
    private val deliveries = mockk<WebhookDeliveryRepository>()
    private val gateway = mockk<WebhookGateway>()
    private val activity = mockk<ActivityReporter>()
    private val now = WebhookFixtures.start.plus(Duration.ofMinutes(5))
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ledger = WebhookDeliveryLedger(endpoints, deliveries, activity)
    private val dispatch = WebhookDispatchService(ledger, gateway, clock)
    private val endpoint = WebhookFixtures.endpoint()
    private val delivery = delivery()

    init {
        every { deliveries.update(any()) } just runs
        every { endpoints.update(any()) } just runs
        every { activity.record(any()) } just runs
        every { endpoints.findById(endpoint.id) } returns endpoint
        every { endpoints.findByIdForUpdate(endpoint.id) } returns endpoint
        every { deliveries.findByIdForUpdate(delivery.id) } returns delivery.claim(now)
    }

    @Test
    fun `a 2xx answer delivers the claimed delivery on its first attempt`() {
        every { deliveries.lockNextDue(now) } returnsMany listOf(delivery, null)
        every { gateway.send(any()) } returns AttemptResult.Answered(204, null)

        val sent = dispatch.dispatchDue()

        assertThat(sent.map { it.progress.state }).containsExactly(DeliveryState.SUCCEEDED)
        assertThat(sent.single().progress.attempts).isEqualTo(1)
        assertThat(sent.single().progress.last).isEqualTo(DeliveryAttempt(now, 204, null))
    }

    @Test
    fun `a failed answer schedules the next attempt five seconds later with its status shown`() {
        every { deliveries.lockNextDue(now) } returnsMany listOf(delivery, null)
        every { gateway.send(any()) } returns AttemptResult.Answered(503, null)

        val sent = dispatch.dispatchDue().single()

        assertThat(sent.progress.state).isEqualTo(DeliveryState.PENDING)
        assertThat(sent.progress.nextAttemptAt).isEqualTo(now.plusSeconds(5))
        assertThat(sent.progress.last?.status).isEqualTo(503)
    }

    @Test
    fun `a delivery whose endpoint stopped after it was taken is not sent`() {
        val stopped = endpoint.stop(StopReason.OWNER, now)
        every { endpoints.findById(endpoint.id) } returns stopped
        every { deliveries.lockNextDue(now) } returnsMany listOf(delivery, null)

        val sent = dispatch.dispatchDue().single()

        assertThat(sent.progress.state).isEqualTo(DeliveryState.PENDING)
        assertThat(sent.progress.last).isNull()
    }

    @Test
    fun `a 410 answer ends the delivery and the receiver's endpoint stops as gone`() {
        every { deliveries.lockNextDue(now) } returnsMany listOf(delivery, null)
        every { gateway.send(any()) } returns AttemptResult.Answered(410, null)

        val sent = dispatch.dispatchDue().single()
        val stopped = ledger.stopGone(endpoint.id, now)

        assertThat(sent.progress.state).isEqualTo(DeliveryState.FAILED)
        assertThat(sent.progress.last?.status).isEqualTo(410)
        assertThat(stopped?.timeline?.stop?.reason).isEqualTo(StopReason.GONE)
    }

    @Test
    fun `nothing due sends nothing`() {
        every { deliveries.lockNextDue(now) } returns null

        assertThat(dispatch.dispatchDue()).isEmpty()
    }

    private fun delivery(): WebhookDelivery {
        val subject = EventSubject(WebhookFixtures.space, WebhookFixtures.document, null, null, 2)
        val facts = EventFacts(3, WebhookFixtures.start, subject, actor())
        val event = WebhookEvent.of(WebhookEventType.DOCUMENT_CHANGED, facts, null, "https://docs.example.com")
        return WebhookDelivery.plan(WebhookDeliveryId(UUID.randomUUID()), endpoint.id, event, WebhookFixtures.start)
    }

    private fun actor(): EventActor = EventActor("agent", "robinjoon", "Claude Code (claude.ai)")
}
