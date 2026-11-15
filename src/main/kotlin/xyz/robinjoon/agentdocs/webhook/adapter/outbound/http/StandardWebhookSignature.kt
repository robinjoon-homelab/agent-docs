package xyz.robinjoon.agentdocs.webhook.adapter.outbound.http

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// The Standard Webhooks symmetric signature: HMAC-SHA256 over "id.timestamp.body" with the decoded secret bytes,
// written as "v1," and the base64 of the digest. Matches the specification's reference implementation and test vector.
internal object StandardWebhookSignature {
    private const val ALGORITHM = "HmacSHA256"

    fun sign(
        key: ByteArray,
        messageId: String,
        timestamp: Long,
        payload: String,
    ): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        val digest = mac.doFinal("$messageId.$timestamp.$payload".toByteArray(Charsets.UTF_8))
        return "v1," + Base64.getEncoder().encodeToString(digest)
    }
}
