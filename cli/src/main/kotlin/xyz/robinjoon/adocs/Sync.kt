package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

internal class Sync(
    private val cache: Path,
    private val readPage: (String, Int) -> JsonNode,
    private val postEdits: (String, String) -> JsonNode,
) {
    constructor(host: Host, http: Http, credentials: Credentials) : this(
        PrivateFiles.userRoot("XDG_CACHE_HOME", ".cache").resolve(host.key),
        {
            document,
            cursor,
            ->
            http.api("GET", "/api/documents/${encoded(document)}/markdown?format=markdown&cursor=$cursor", credentials.token())
        },
        { document, body -> http.api("POST", "/api/documents/${encoded(document)}/edits", credentials.token(), body) },
    )

    fun pull(
        document: String,
        file: Path,
    ): Map<String, Any> =
        locked(file) {
            val push = pendingState(file)
            require(push == null || (push.has("conflict") && push.path("document").asString() == document)) {
                "pending push exists; retry push or recover its original document"
            }
            if (push != null) {
                return@locked ConflictRecovery(file, pending(file)).pull(push, { download(document) }) { saveBaseline(file, document, it) }
            }
            val text = download(document)
            Files.writeString(file, text, StandardOpenOption.CREATE_NEW)
            saveBaseline(file, document, text)
            mapOf("documentId" to document, "file" to file.toString())
        }

    fun push(file: Path): JsonNode =
        locked(file) {
            val pending = pendingState(file)
            if (pending != null && !pending.path("recovered").asBoolean(false)) return@locked retry(file)
            val original = Json.read(Files.readString(baseline(file)))
            val edited = Files.readString(file)
            val operations = Diff.operations(original.path("markdown").asString(), edited)
            if (operations.isEmpty()) {
                Files.deleteIfExists(pending(file))
                return@locked Json.read("{\"unchanged\":true}")
            }
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
        saved.get("conflict")?.let { throw CliFailure.fromServer(it, 409) }
        requireSubmittedFile(file, saved.path("edited").asString(), saved.get("canonical")?.stringValue())
        val document = saved.path("document").asString()
        val responseFile = cache.resolve(key(file) + ".response.json")
        val result =
            if (saved.has("canonical")) {
                Json.read(Files.readString(responseFile))
            } else {
                submit(file, saved)
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
        requireSubmittedFile(file, saved.path("edited").asString(), text)
        Files.writeString(file, text)
        saveBaseline(file, document, text)
        Files.delete(pending(file))
        return result
    }

    private fun requireSubmittedFile(
        file: Path,
        edited: String,
        canonical: String?,
    ) {
        val local = Files.readString(file)
        if (local != edited && local != canonical) {
            throw CliFailure("local_changes", "Save the new local edits separately and restore the submitted file before retrying push")
        }
    }

    private fun submit(
        file: Path,
        saved: JsonNode,
    ): JsonNode {
        val document = saved.path("document").asString()
        try {
            return postEdits(document, saved.path("body").asString())
        } catch (failure: CliFailure) {
            if (failure.status != 409 || failure.code != "conflict") throw failure
            val fix = "${failure.fix}. Pull the same document and file to back up local edits, then merge and push again"
            val rejection = CliFailure(failure.code, fix, failure.status, failure.retryAfter, cause = failure, evidence = failure.evidence)
            PrivateFiles.write(pending(file), Json.write(mapOf("document" to document, "conflict" to Json.read(rejection.output(true)))))
            throw rejection
        }
    }

    fun download(document: String): String {
        val chunks = MarkdownChunks(document)
        do {
            chunks.accept(readPage(document, chunks.cursor))
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

    private fun pendingState(file: Path): JsonNode? = pending(file).takeIf(Files::exists)?.let { Json.read(Files.readString(it)) }

    private fun <T> locked(
        file: Path,
        action: () -> T,
    ): T = PrivateFiles.locked(cache.resolve(key(file) + ".lock"), action)
}
