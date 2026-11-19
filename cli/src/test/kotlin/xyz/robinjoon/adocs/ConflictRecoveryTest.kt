package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class ConflictRecoveryTest {
    @Test
    fun `retry returns the original backup after an interruption and a lost final response`(
        @TempDir dir: Path,
    ) {
        val file = Files.writeString(dir.resolve("draft.md"), "my edits")
        val pending = dir.resolve("cache/draft.pending.json")
        val recovery = ConflictRecovery(file, pending)
        val rejected = Json.read("""{"document":"doc","conflict":{"code":"conflict"}}""")
        assertThrows(IOException::class.java) {
            recovery.pull(rejected, { "server version" }) { throw IOException("interrupted before committing the baseline") }
        }

        val prepared = Json.read(Files.readString(pending))
        val originalBackup = Path.of(prepared.path("localEdits").asString())
        assertEquals("my edits", Files.readString(originalBackup))
        Files.writeString(file, "new editor input")
        val refusal = assertThrows(CliFailure::class.java) { recovery.pull(prepared, { "newer server version" }) {} }
        assertEquals("local_changes", refusal.code)
        assertEquals("new editor input", Files.readString(file))

        Files.move(file, dir.resolve("other-input.md"))
        val recovered = recovery.pull(prepared, { "newer server version" }) {}
        assertEquals(originalBackup.toString(), recovered["localEdits"])
        assertEquals("server version", Files.readString(file))
        Files.writeString(file, "merged")
        val repeated = recovery.pull(Json.read(Files.readString(pending)), { "newest server version" }) {}
        assertEquals(recovered, repeated)
        assertEquals("merged", Files.readString(file))
        assertEquals("my edits", Files.readString(originalBackup))
    }
}
