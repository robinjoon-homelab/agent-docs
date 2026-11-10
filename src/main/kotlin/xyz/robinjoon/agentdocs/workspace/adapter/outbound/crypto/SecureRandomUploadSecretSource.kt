package xyz.robinjoon.agentdocs.workspace.adapter.outbound.crypto

import xyz.robinjoon.agentdocs.workspace.application.port.output.UploadSecretSource
import java.security.SecureRandom
import java.util.Base64

class SecureRandomUploadSecretSource(
    private val random: SecureRandom = SecureRandom(),
) : UploadSecretSource {
    override fun next(): String {
        val bytes = ByteArray(SECRET_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val SECRET_BYTES = 32
    }
}
