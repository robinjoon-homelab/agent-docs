package xyz.robinjoon.agentdocs.access.domain

import java.security.MessageDigest

// Tokens and codes are stored only as their SHA-256, so a copy of the database grants nothing.
@JvmInline
value class TokenHash(
    val value: String,
) {
    init {
        if (!pattern.matches(value)) {
            throw InvalidAccessException("a token hash is 64 lowercase hex digits")
        }
    }

    override fun toString(): String = value

    companion object {
        private val pattern = Regex("[0-9a-f]{64}")
        private const val BYTE_MASK = 0xff
        private const val HEX_RADIX = 16

        fun of(secret: String): TokenHash =
            TokenHash(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(secret.toByteArray())
                    .joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') },
            )
    }
}
