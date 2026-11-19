package xyz.robinjoon.agentdocs.webhook.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryAttempt
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryState
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventActor
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventFacts
import xyz.robinjoon.agentdocs.webhook.domain.delivery.EventSubject
import xyz.robinjoon.agentdocs.webhook.domain.delivery.RetrySchedule
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDeliveryId
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookEvent
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import java.time.Duration
import java.time.Instant
import java.util.UUID

class WebhookDeliveryTest {
    private val at = Instant.parse("2026-11-16T03:00:00Z")
    private val id = WebhookDeliveryId(UUID.fromString("55555555-5555-4555-8555-555555555555"))

    @Test
    fun `a planned delivery is due at once and keeps one webhook id without dots`() {
        val delivery = planned()

        assertThat(delivery.isDue(at)).isTrue()
        assertThat(delivery.isDue(at.minusMillis(1))).isFalse()
        assertThat(delivery.id.messageId).isEqualTo("msg_55555555555545558555555555555555")
    }

    @Test
    fun `failures retry after 5s, 30s, 2m, 10m, 1h, 6h, and 24h and the eighth failure ends it`() {
        var delivery = planned()
        var now = at
        val waits = ArrayList<Duration>()
        repeat(RetrySchedule.ATTEMPTS) {
            val claimed = delivery.claim(now)
            delivery = requireNotNull(claimed.record(claimed.progress.attempts, AttemptResult.Answered(500), now))
            delivery.progress.nextAttemptAt?.let { next ->
                waits.add(Duration.between(now, next))
                now = next
            }
        }

        assertThat(waits).containsExactlyElementsOf(RetrySchedule.delays)
        assertThat(waits.map { it.seconds }).containsExactly(5, 30, 120, 600, 3_600, 21_600, 86_400)
        assertThat(delivery.progress.state).isEqualTo(DeliveryState.FAILED)
        assertThat(delivery.progress.attempts).isEqualTo(8)
        assertThat(delivery.progress.last).isEqualTo(DeliveryAttempt(now, 500, null))
    }

    @Test
    fun `a 2xx answer delivers and a 410 answer ends the delivery without retry`() {
        val claimed = planned().claim(at)

        val delivered = claimed.record(1, AttemptResult.Answered(204), at.plusSeconds(1))
        val gone = claimed.record(1, AttemptResult.Answered(410), at.plusSeconds(1))

        assertThat(delivered?.progress?.state).isEqualTo(DeliveryState.SUCCEEDED)
        assertThat(delivered?.progress?.nextAttemptAt).isNull()
        assertThat(gone?.progress?.state).isEqualTo(DeliveryState.FAILED)
        assertThat(gone?.progress?.last?.status).isEqualTo(410)
    }

    @Test
    fun `redirect and throttling responses retry on the same schedule`() {
        val claimed = planned().claim(at)

        for (status in listOf(302, 429, 503)) {
            val failed = claimed.record(1, AttemptResult.Answered(status), at)
            assertThat(failed?.progress?.nextAttemptAt).isEqualTo(at.plusSeconds(5))
        }
    }

    @Test
    fun `no answer is recorded as a short error code and retried`() {
        val failed = planned().claim(at).record(1, AttemptResult.Unanswered("timeout"), at.plusSeconds(15))

        assertThat(failed?.progress?.state).isEqualTo(DeliveryState.PENDING)
        assertThat(failed?.progress?.last).isEqualTo(DeliveryAttempt(at.plusSeconds(15), null, "timeout"))
        assertThat(failed?.progress?.nextAttemptAt).isEqualTo(at.plusSeconds(20))
    }

    @Test
    fun `a claimed attempt is held for the lease and a result for an older attempt changes nothing`() {
        val claimed = planned().claim(at)
        val retaken = claimed.claim(at.plus(WebhookDelivery.lease))

        assertThat(claimed.isDue(at.plus(WebhookDelivery.lease).minusMillis(1))).isFalse()
        assertThatThrownBy { claimed.claim(at.plusSeconds(1)) }.isInstanceOf(IllegalStateException::class.java)
        assertThat(retaken.progress.attempts).isEqualTo(2)
        assertThat(retaken.record(1, AttemptResult.Answered(200), at)).isNull()
    }

    @Test
    fun `when the last attempt's lease ends with no answer recorded the delivery fails instead of a ninth request`() {
        var delivery = planned()
        var now = at
        repeat(RetrySchedule.ATTEMPTS - 1) {
            val claimed = delivery.claim(now)
            delivery = requireNotNull(claimed.record(claimed.progress.attempts, AttemptResult.Unanswered("timeout"), now))
            now = requireNotNull(delivery.progress.nextAttemptAt)
        }
        val last = delivery.claim(now)
        val expired = now.plus(WebhookDelivery.lease)

        val ended = last.claim(expired)

        assertThat(last.inFlight).isTrue()
        assertThat(ended.inFlight).isFalse()
        assertThat(ended.progress.state).isEqualTo(DeliveryState.FAILED)
        assertThat(ended.progress.attempts).isEqualTo(8)
        assertThat(ended.progress.last).isEqualTo(DeliveryAttempt(expired, null, WebhookDelivery.UNRECORDED))
    }

    private fun planned(): WebhookDelivery {
        val subject = EventSubject(UUID.randomUUID(), UUID.randomUUID(), null, null, 3)
        val facts = EventFacts(42, at, subject, EventActor("human", "robinjoon", null))
        val event = WebhookEvent.of(WebhookEventType.DOCUMENT_CHANGED, facts, null, "https://docs.example.com")
        return WebhookDelivery.plan(id, WebhookEndpointId(UUID.randomUUID()), event, at)
    }
}
