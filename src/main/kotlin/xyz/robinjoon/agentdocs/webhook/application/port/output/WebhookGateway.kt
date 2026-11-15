package xyz.robinjoon.agentdocs.webhook.application.port.output

import xyz.robinjoon.agentdocs.webhook.application.model.OutgoingWebhook
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult

// Sends one signed request outside any transaction. It does not throw for a failed request; the failure is the result.
interface WebhookGateway {
    fun send(request: OutgoingWebhook): AttemptResult
}
