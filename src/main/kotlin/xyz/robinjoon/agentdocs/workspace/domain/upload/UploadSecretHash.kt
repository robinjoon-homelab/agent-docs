package xyz.robinjoon.agentdocs.workspace.domain.upload

import java.security.MessageDigest
import java.util.HexFormat

// Upload secrets are stored only as their SHA-256, so a copy of the database uploads nothing.
@JvmInline
value class UploadSecretHash(
    val value: String,
) {
    companion object {
        fun of(secret: String): UploadSecretHash =
            UploadSecretHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())))
    }
}
