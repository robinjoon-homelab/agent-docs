package xyz.robinjoon.agentdocs.access.application.port.output

interface SecretSource {
    // 32 random bytes from a cryptographic generator, base64url without padding.
    fun next(): String
}
