package xyz.robinjoon.agentdocs.webhook.application.port.input

import xyz.robinjoon.agentdocs.webhook.application.model.EndpointRegistration
import xyz.robinjoon.agentdocs.webhook.application.model.IssuedEndpoint
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookOperator
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId

// The owner's management of endpoints. There is no delete: an endpoint is stopped and kept with its delivery records.
interface WebhookEndpointUseCase {
    fun list(operator: WebhookOperator): List<WebhookEndpoint>

    fun register(
        operator: WebhookOperator,
        registration: EndpointRegistration,
    ): IssuedEndpoint

    fun stop(
        operator: WebhookOperator,
        id: WebhookEndpointId,
    ): WebhookEndpoint

    fun rotateSecret(
        operator: WebhookOperator,
        id: WebhookEndpointId,
    ): IssuedEndpoint

    // The most recent deliveries first.
    fun deliveries(
        operator: WebhookOperator,
        id: WebhookEndpointId,
        limit: Int,
    ): List<WebhookDelivery>
}
