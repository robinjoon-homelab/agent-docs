package xyz.robinjoon.agentdocs.workspace.adapter.outbound.storage

import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

// Keeps files under <root>/<first two hex digits>/<sha256> for local development. A file is written to a temporary
// name and moved into place, so a reader never sees half a file.
class FileSystemAssetStore(
    private val root: Path,
) : AssetStore {
    override fun put(
        sha256: String,
        mediaType: String,
        bytes: ByteArray,
    ) {
        val target = pathOf(sha256)
        if (Files.exists(target)) return
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, sha256, ".part")
        Files.write(temporary, bytes)
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun get(sha256: String): ByteArray {
        val path = pathOf(sha256)
        if (!Files.exists(path)) throw AssetContentMissingException(sha256)
        return Files.readAllBytes(path)
    }

    private fun pathOf(sha256: String): Path {
        require(hexPattern.matches(sha256)) { "not a content hash: $sha256" }
        return root.resolve(sha256.take(2)).resolve(sha256)
    }

    private companion object {
        val hexPattern = Regex("[0-9a-f]{64}")
    }
}
