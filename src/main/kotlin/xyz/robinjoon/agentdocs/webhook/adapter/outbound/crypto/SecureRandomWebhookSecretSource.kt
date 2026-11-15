package xyz.robinjoon.agentdocs.webhook.adapter.outbound.crypto

import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookSecretSource
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import java.security.SecureRandom

class SecureRandomWebhookSecretSource(
    private val random: SecureRandom = SecureRandom(),
) : WebhookSecretSource {
    override fun generate(): WebhookSecret {
        val bytes = ByteArray(SECRET_BYTES)
        random.nextBytes(bytes)
        return WebhookSecret.of(bytes)
    }

    private companion object {
        const val SECRET_BYTES = 32
    }
}
