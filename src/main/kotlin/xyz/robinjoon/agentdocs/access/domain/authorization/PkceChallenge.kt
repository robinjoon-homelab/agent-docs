package xyz.robinjoon.agentdocs.access.domain.authorization

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import java.security.MessageDigest
import java.util.Base64

// A PKCE S256 code challenge. Plain challenges are not accepted.
@JvmInline
value class PkceChallenge(
    val value: String,
) {
    init {
        if (!challengePattern.matches(value)) {
            throw InvalidAccessException("code_challenge must be a base64url SHA-256 of 43 characters")
        }
    }

    fun matches(verifier: String): Boolean {
        if (!verifierPattern.matches(verifier)) return false
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest) == value
    }

    private companion object {
        val challengePattern = Regex("[A-Za-z0-9_-]{43}")
        val verifierPattern = Regex("[A-Za-z0-9._~-]{43,128}")
    }
}
