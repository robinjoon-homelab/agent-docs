package xyz.robinjoon.agentdocs.webhook.domain.endpoint

import xyz.robinjoon.agentdocs.webhook.domain.InvalidWebhookException
import java.net.URI

// The address the owner chose. http and private homelab hosts are allowed because only the owner sets it. User names
// and passwords in the address, other schemes, fragments, and link-local, unspecified, multicast, or broadcast literals
// are refused here; host names are resolved and checked again before every attempt, because a name can later point
// anywhere, including cloud metadata addresses.
class WebhookUrl private constructor(
    val value: URI,
) {
    val host: String get() = value.host

    override fun equals(other: Any?): Boolean = other is WebhookUrl && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value.toString()

    companion object {
        private const val MAX_LENGTH = 2000
        private val schemes = setOf("http", "https")
        private val ipv4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val linkLocalV6 = listOf("fe8", "fe9", "fea", "feb")
        private val unspecifiedV6 = setOf("::", "::0", "0:0:0:0:0:0:0:0")

        fun of(text: String): WebhookUrl {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) invalid("url must be 1 to $MAX_LENGTH characters")
            val uri = parse(trimmed)
            if (uri.scheme !in schemes) invalid("url must start with http:// or https://")
            if (uri.rawUserInfo != null) invalid("url must not carry a user name or password")
            if (uri.rawFragment != null) invalid("url must not have a fragment")
            val host = uri.host
            if (host.isNullOrBlank()) invalid("url must name a host")
            if (blocked(host)) invalid("url must not point at a link-local, unspecified, multicast, or broadcast address")
            return WebhookUrl(uri)
        }

        // Reads what the webhook context itself stored.
        fun restore(text: String): WebhookUrl = WebhookUrl(URI.create(text))

        private fun parse(text: String): URI =
            try {
                URI.create(text)
            } catch (exception: IllegalArgumentException) {
                throw InvalidWebhookException("url is not a valid address", exception)
            }

        private fun blocked(host: String): Boolean {
            val literal = host.lowercase().removePrefix("[").removeSuffix("]")
            val mapped = literal.removePrefix(MAPPED_V4)
            return when {
                ipv4.matches(mapped) -> blockedV4(mapped)
                host.startsWith("[") -> literal in unspecifiedV6 || literal.startsWith("ff") || linkLocalV6.any(literal::startsWith)
                else -> false
            }
        }

        // 0.0.0.0/8 is "this network", 169.254.0.0/16 link-local with the cloud metadata address, 224.0.0.0/4 multicast,
        // and 240.0.0.0/4 reserved up to the broadcast address.
        private fun blockedV4(literal: String): Boolean {
            val octets = literal.split('.').map(String::toInt)
            val first = octets[0]
            return first == 0 || (first == LINK_LOCAL_FIRST && octets[1] == LINK_LOCAL_SECOND) || first >= MULTICAST_FIRST
        }

        private fun invalid(message: String): Nothing = throw InvalidWebhookException(message)

        private const val MAPPED_V4 = "::ffff:"
        private const val LINK_LOCAL_FIRST = 169
        private const val LINK_LOCAL_SECOND = 254
        private const val MULTICAST_FIRST = 224
    }
}
