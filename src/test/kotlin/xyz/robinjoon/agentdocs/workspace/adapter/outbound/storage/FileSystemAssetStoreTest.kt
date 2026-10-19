package xyz.robinjoon.agentdocs.workspace.adapter.outbound.storage

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import xyz.robinjoon.agentdocs.workspace.application.model.AssetContentMissingException
import java.nio.file.Path

class FileSystemAssetStoreTest {
    private val sha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun `stored bytes read back by their hash and a second write keeps the first`(
        @TempDir root: Path,
    ) {
        val store = FileSystemAssetStore(root)

        store.put(sha, "image/png", "abc".toByteArray())
        store.put(sha, "image/png", "abc".toByteArray())

        assertThat(store.get(sha)).isEqualTo("abc".toByteArray())
        assertThat(root.resolve("ba").resolve(sha)).exists()
    }

    @Test
    fun `a hash without bytes and a path instead of a hash are refused`(
        @TempDir root: Path,
    ) {
        val store = FileSystemAssetStore(root)

        assertThatThrownBy { store.get(sha) }.isInstanceOf(AssetContentMissingException::class.java)
        assertThatThrownBy { store.get("../../etc/passwd") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
