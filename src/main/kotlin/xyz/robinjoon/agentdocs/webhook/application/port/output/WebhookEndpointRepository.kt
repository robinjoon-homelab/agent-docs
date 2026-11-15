package xyz.robinjoon.agentdocs.webhook.application.port.output

import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId

interface WebhookEndpointRepository {
    fun add(endpoint: WebhookEndpoint)

    fun update(endpoint: WebhookEndpoint)

    fun findById(id: WebhookEndpointId): WebhookEndpoint?

    fun findByIdForUpdate(id: WebhookEndpointId): WebhookEndpoint?

    // Every endpoint, stopped ones included, newest first.
    fun list(): List<WebhookEndpoint>

    // The endpoints that are not stopped, locked until the caller's transaction ends.
    fun lockActive(): List<WebhookEndpoint>
}
