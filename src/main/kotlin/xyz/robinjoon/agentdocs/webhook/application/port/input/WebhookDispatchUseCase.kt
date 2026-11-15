package xyz.robinjoon.agentdocs.webhook.application.port.input

import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery

interface WebhookDispatchUseCase {
    // Sends a batch of due deliveries and returns them as recorded after their attempt.
    fun dispatchDue(): List<WebhookDelivery>
}
