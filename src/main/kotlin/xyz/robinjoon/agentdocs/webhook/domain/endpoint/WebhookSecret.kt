package xyz.robinjoon.agentdocs.webhook.domain.endpoint

import xyz.robinjoon.agentdocs.webhook.domain.InvalidWebhookException
import java.security.MessageDigest
import java.util.Base64

// The signing secret as Standard Webhooks writes it: whsec_ and the base64 of 24 to 64 random bytes. The HMAC key is
// the decoded bytes. Unlike tokens it cannot be kept as a hash, because every attempt signs again, so it is stored as
// written and shown only when it is created or replaced. toString never shows it.
class WebhookSecret private constructor(
    private val key: ByteArray,
) {
    val serialized: String get() = PREFIX + Base64.getEncoder().encodeToString(key)

    fun key(): ByteArray = key.copyOf()

    override fun equals(other: Any?): Boolean = other is WebhookSecret && MessageDigest.isEqual(other.key, key)

    override fun hashCode(): Int = key.contentHashCode()

    override fun toString(): String = "WebhookSecret(****)"

    companion object {
        const val PREFIX = "whsec_"
        private const val MIN_BYTES = 24
        private const val MAX_BYTES = 64

        fun of(bytes: ByteArray): WebhookSecret {
            if (bytes.size !in MIN_BYTES..MAX_BYTES) throw InvalidWebhookException("a signing secret is $MIN_BYTES to $MAX_BYTES bytes")
            return WebhookSecret(bytes.copyOf())
        }

        fun parse(serialized: String): WebhookSecret {
            if (!serialized.startsWith(PREFIX)) throw InvalidWebhookException("a signing secret starts with $PREFIX")
            val bytes =
                try {
                    Base64.getDecoder().decode(serialized.removePrefix(PREFIX))
                } catch (exception: IllegalArgumentException) {
                    throw InvalidWebhookException("a signing secret is base64 after $PREFIX", exception)
                }
            return of(bytes)
        }
    }
}
