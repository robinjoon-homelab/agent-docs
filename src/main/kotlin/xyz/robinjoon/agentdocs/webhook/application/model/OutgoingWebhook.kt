package xyz.robinjoon.agentdocs.webhook.application.model

import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import java.net.URI
import java.time.Instant

// One request as Standard Webhooks frames it. messageId stays the same across retries; attemptAt is this attempt's
// time and goes into the signature. A sender starts signing by looking up the current key after preparing the request.
data class OutgoingWebhook(
    val url: URI,
    val messageId: String,
    val attemptAt: Instant,
    val payload: String,
    val signingSecret: () -> WebhookSecret?,
)
