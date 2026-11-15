package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal class Sync(
    private val host: Host,
    private val http: Http,
    private val credentials: Credentials,
) {
    private val cache = PrivateFiles.userRoot("XDG_CACHE_HOME", ".cache").resolve(host.key)

    fun pull(
        document: String,
        file: Path,
    ): Map<String, Any> =
        locked(file) {
            require(!Files.exists(pending(file))) { "pending push exists; retry push before pulling" }
            val text = download(document)
            // Never destroy an existing local edit implicitly.
            Files.writeString(file, text, java.nio.file.StandardOpenOption.CREATE_NEW)
            saveBaseline(file, document, text)
            mapOf("documentId" to document, "file" to file.toString())
        }

    fun push(file: Path): JsonNode =
        locked(file) {
            if (Files.exists(pending(file))) return@locked retry(file)
            val original = Json.read(Files.readString(baseline(file)))
            val edited = Files.readString(file)
            val operations = Diff.operations(original.path("markdown").asString(), edited)
            if (operations.isEmpty()) return@locked Json.read("{\"unchanged\":true}")
            val request = Json.write(mapOf("opId" to UUID.randomUUID().toString(), "operations" to operations))
            PrivateFiles.write(
                pending(file),
                Json.write(
                    mapOf(
                        "document" to original.path("document").asString(),
                        "body" to request,
                        "edited" to edited,
                    ),
                ),
            )
            retry(file)
        }

    private fun retry(file: Path): JsonNode {
        val saved = Json.read(Files.readString(pending(file)))
        require(Files.readString(file) in listOf(saved.path("edited").asString(), saved.path("canonical").asString())) {
            "pending push: restore the submitted file before retrying"
        }
        val document = saved.path("document").asString()
        val responseFile = cache.resolve(key(file) + ".response.json")
        val result =
            if (saved.has("canonical")) {
                Json.read(Files.readString(responseFile))
            } else {
                http.api("POST", "/api/documents/${encoded(document)}/edits", credentials.token(), saved.path("body").asString())
            }
        // Persist the first response before fetching a new baseline. Never generate a replacement opId on a failure.
        PrivateFiles.write(cache.resolve(key(file) + ".response.json"), Json.write(result))
        val text = if (saved.has("canonical")) saved.path("canonical").asString() else download(document)
        val completed =
            mapOf(
                "document" to document,
                "body" to saved.path("body").asString(),
                "edited" to saved.path("edited").asString(),
                "canonical" to text,
            )
        PrivateFiles.write(pending(file), Json.write(completed))
        Files.writeString(file, text)
        saveBaseline(file, document, text)
        Files.delete(pending(file))
        return result
    }

    fun download(document: String): String {
        val chunks = MarkdownChunks(document)
        do {
            val chunk =
                http.api(
                    "GET",
                    "/api/documents/${encoded(document)}/markdown?format=annotated&cursor=${chunks.cursor}",
                    credentials.token(),
                )
            chunks.accept(chunk)
        } while (chunks.cursor >= 0)
        return chunks.text()
    }

    private fun saveBaseline(
        file: Path,
        document: String,
        text: String,
    ) {
        PrivateFiles.write(baseline(file), Json.write(mapOf("document" to document, "markdown" to text)))
    }

    private fun key(file: Path): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(
                file
                    .toAbsolutePath()
                    .normalize()
                    .toString()
                    .toByteArray(),
            ).joinToString("") { "%02x".format(it) }

    private fun baseline(file: Path): Path = cache.resolve(key(file) + ".json")

    private fun pending(file: Path): Path = cache.resolve(key(file) + ".pending.json")

    private fun <T> locked(
        file: Path,
        action: () -> T,
    ): T = PrivateFiles.locked(cache.resolve(key(file) + ".lock"), action)
}
