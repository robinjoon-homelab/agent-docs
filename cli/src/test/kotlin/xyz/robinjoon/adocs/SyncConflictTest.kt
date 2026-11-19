package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.JsonNode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class SyncConflictTest {
    private val original = "<!-- b:para01 h:original -->\noriginal\n"
    private val head = "<!-- b:para01 h:current -->\nremote\n"
    private val conflicts = Json.read("""[{"blockId":"para01","currentHash":"current","currentMarkdown":"remote"}]""")

    @Test
    fun `a refused push preserves the edit and exposes evidence before refreshing and pushing from the same path`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("draft.md")
        var remote = original
        val sync =
            Sync(dir.resolve("cache"), { _, _ -> page(remote) }) { _, body ->
                val operation = Json.read(body).path("operations").first()
                if (operation.path("ifHash").asString() != "current") {
                    throw CliFailure("conflict", "Read current block", 409, evidence = mapOf("conflicts" to conflicts))
                }
                remote = operation.path("markdown").asString() + "\n"
                Json.read("""{"revision":3}""")
            }
        sync.pull("doc", file)
        val local = original.replace("\noriginal\n", "\nmine\n")
        Files.writeString(file, local)
        remote = head

        val refused = assertThrows(CliFailure::class.java) { sync.push(file) }
        assertEquals(conflicts, Json.read(refused.output(true)).get("conflicts"))
        assertTrue(refused.output(false).contains("currentMarkdown"))
        assertEquals(local, Files.readString(file))
        assertThrows(IllegalArgumentException::class.java) { sync.pull("another", file) }
        Files.writeString(file, local + "new local text")
        val refresh = sync.pull("doc", file)
        assertEquals(local + "new local text", Files.readString(Path.of(refresh.getValue("localEdits").toString())))
        assertEquals(head, Files.readString(file))
        val merged = head.replace("remote", "remote and mine")
        Files.writeString(file, merged)
        assertEquals(3, sync.push(file).path("revision").asInt())
        assertEquals(merged, Files.readString(file))
    }

    @Test
    fun `an uncertain push cannot be replaced by a pull or replayed with a new operation id`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("draft.md")
        val cache = dir.resolve("cache")
        val local = original.replace("\noriginal\n", "\nmine\n")
        var committed: String? = null
        val sync =
            Sync(cache, { _, _ -> page(original) }) { _, request ->
                committed = request
                throw IOException("response lost after commit")
            }
        sync.pull("doc", file)
        Files.writeString(file, local)
        assertThrows(IOException::class.java) { sync.push(file) }

        val restarted =
            Sync(cache, { _, _ -> page(local) }) { _, request ->
                if (request != committed) throw IllegalStateException("would apply a second write")
                Json.read("""{"revision":2}""")
            }
        assertThrows(IllegalArgumentException::class.java) { restarted.pull("doc", file) }
        assertEquals(local, Files.readString(file))
        assertEquals(2, restarted.push(file).path("revision").asInt())
    }

    @Test
    fun `working edits made after submission survive a successful response`(
        @TempDir dir: Path,
    ) {
        val file = dir.resolve("draft.md")
        val sync =
            Sync(dir.resolve("cache"), { _, _ -> page(original) }) { _, _ ->
                Files.writeString(file, "new input")
                Json.read("""{"revision":2}""")
            }
        sync.pull("doc", file)
        Files.writeString(file, original.replace("\noriginal\n", "\nmine\n"))

        assertEquals("local_changes", assertThrows(CliFailure::class.java) { sync.push(file) }.code)
        assertEquals("new input", Files.readString(file))
    }

    private fun page(markdown: String): JsonNode =
        Json.read(Json.write(mapOf("documentId" to "doc", "version" to 1, "markdown" to markdown, "contiguous" to true)))
}
