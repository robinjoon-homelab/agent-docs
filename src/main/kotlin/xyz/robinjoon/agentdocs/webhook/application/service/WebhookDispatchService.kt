package xyz.robinjoon.agentdocs.webhook.application.service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.webhook.application.model.DeliveryClaim
import xyz.robinjoon.agentdocs.webhook.application.model.OutgoingWebhook
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookDispatchUseCase
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookGateway
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import java.time.Clock
import java.time.Instant

// Requests go out with no transaction open, so a slow receiver never holds a connection or a row lock. Deliveries are
// taken one at a time and signed right before their request, so the signed timestamp is the attempt's own.
@Transactional(readOnly = true)
class WebhookDispatchService(
    private val ledger: WebhookDeliveryLedger,
    private val gateway: WebhookGateway,
    private val clock: Clock,
) : WebhookDispatchUseCase {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun dispatchDue(): List<WebhookDelivery> {
        val done = ArrayList<WebhookDelivery>()
        while (done.size < BATCH) {
            val claim = ledger.claimNext(Instant.now(clock)) ?: break
            done.add(attempt(claim))
        }
        return done.toList()
    }

    private fun attempt(claim: DeliveryClaim): WebhookDelivery {
        val endpoint = claim.endpoint ?: return claim.delivery
        val delivery = claim.delivery
        val request =
            OutgoingWebhook(
                url = endpoint.subscription.url.value,
                messageId = delivery.id.messageId,
                attemptAt = Instant.now(clock),
                payload = delivery.event.payload,
                secret = endpoint.secret,
            )
        val result = gateway.send(request)
        val recorded = ledger.complete(delivery, result, Instant.now(clock))
        if (result.gone) ledger.stopGone(endpoint.id, Instant.now(clock))
        return recorded
    }

    private companion object {
        const val BATCH = 20
    }
}
