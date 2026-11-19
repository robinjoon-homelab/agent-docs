package xyz.robinjoon.agentdocs.webhook.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.webhook.application.model.DeliveryClaim
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import java.time.Instant

// The short transactions around one attempt: take a due delivery, record what the request produced, and stop an
// endpoint whose receiver answered 410. Each changes one root, and the request itself runs between them with no
// transaction open.
@Transactional(readOnly = true)
class WebhookDeliveryLedger(
    private val endpoints: WebhookEndpointRepository,
    private val deliveries: WebhookDeliveryRepository,
    private val activity: ActivityReporter,
) {
    @Transactional
    fun claimNext(at: Instant): DeliveryClaim? {
        val due = deliveries.lockNextDue(at) ?: return null
        val claimed = due.claim(at)
        deliveries.update(claimed)
        val endpoint = if (claimed.inFlight) endpoints.findById(claimed.endpointId)?.takeIf { it.active } else null
        return DeliveryClaim(claimed, endpoint)
    }

    fun signingSecret(id: WebhookEndpointId): WebhookSecret? = endpoints.findById(id)?.takeIf { it.active }?.secret

    // A result for an attempt that is no longer current changes nothing and returns the delivery as it is.
    @Transactional
    fun complete(
        claimed: WebhookDelivery,
        result: AttemptResult,
        at: Instant,
    ): WebhookDelivery {
        val current = deliveries.findByIdForUpdate(claimed.id) ?: return claimed
        val recorded = current.record(claimed.progress.attempts, result, at) ?: return current
        deliveries.update(recorded)
        return recorded
    }

    // Kept apart from recording the 410: if it does not commit, the next 410 from the same receiver stops it.
    @Transactional
    fun stopGone(
        id: WebhookEndpointId,
        at: Instant,
    ): WebhookEndpoint? {
        val current = endpoints.findByIdForUpdate(id) ?: return null
        if (!current.active) return current
        val stopped = current.stop(StopReason.GONE, at)
        endpoints.update(stopped)
        val summary = "webhook 중지(수신처가 410 응답): ${stopped.subscription.url.host}".take(SUMMARY_LENGTH)
        activity.record(ActivityRecord("webhook.stopped", ActivityActor.system, ActivitySubject(null, null, null, null), summary, at))
        return stopped
    }

    private companion object {
        const val SUMMARY_LENGTH = 300
    }
}
