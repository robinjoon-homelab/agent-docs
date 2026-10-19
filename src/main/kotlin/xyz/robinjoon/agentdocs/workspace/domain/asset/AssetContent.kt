package xyz.robinjoon.agentdocs.workspace.domain.asset

import java.security.MessageDigest

// The stored bytes are identified by their SHA-256, so the same file uploaded twice is stored once and an asset never
// changes after upload.
data class AssetContent(
    val mediaType: AssetMediaType,
    val size: Long,
    val sha256: String,
) {
    init {
        if (size !in 1..MAX_SIZE) {
            throw InvalidAssetException("a file must be 1 byte to ${MAX_SIZE / MEGABYTE}MB")
        }
        if (!hexPattern.matches(sha256)) {
            throw InvalidAssetException("content hash must be 64 lowercase hex digits")
        }
    }

    companion object {
        const val MEGABYTE = 1024L * 1024L
        const val MAX_SIZE = 20 * MEGABYTE
        private val hexPattern = Regex("[0-9a-f]{64}")
        private const val BYTE_MASK = 0xff
        private const val HEX_RADIX = 16

        fun of(
            mediaType: AssetMediaType,
            bytes: ByteArray,
        ): AssetContent {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return AssetContent(
                mediaType,
                bytes.size.toLong(),
                digest.joinToString("") {
                    (it.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
                },
            )
        }
    }
}
