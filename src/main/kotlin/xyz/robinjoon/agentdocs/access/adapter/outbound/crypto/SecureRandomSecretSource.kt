package xyz.robinjoon.agentdocs.access.adapter.outbound.crypto

import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import java.security.SecureRandom
import java.util.Base64

class SecureRandomSecretSource(
    private val random: SecureRandom = SecureRandom(),
) : SecretSource {
    override fun next(): String {
        val bytes = ByteArray(SECRET_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val SECRET_BYTES = 32
    }
}
