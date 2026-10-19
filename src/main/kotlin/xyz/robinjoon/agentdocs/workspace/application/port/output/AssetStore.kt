package xyz.robinjoon.agentdocs.workspace.application.port.output

// Keeps file bytes under their content hash. Writing the same hash twice is harmless because the bytes are the same.
interface AssetStore {
    fun put(
        sha256: String,
        mediaType: String,
        bytes: ByteArray,
    )

    // Fails with AssetContentMissingException when no bytes are stored under the hash.
    fun get(sha256: String): ByteArray
}
