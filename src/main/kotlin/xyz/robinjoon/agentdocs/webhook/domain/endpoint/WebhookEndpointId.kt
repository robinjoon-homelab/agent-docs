package xyz.robinjoon.agentdocs.webhook.domain.endpoint

import java.util.UUID

@JvmInline
value class WebhookEndpointId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
