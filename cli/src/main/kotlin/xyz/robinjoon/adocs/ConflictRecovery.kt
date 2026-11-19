package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID

internal class ConflictRecovery(
    private val file: Path,
    private val pending: Path,
) {
    fun pull(
        saved: JsonNode,
        download: () -> String,
        saveBaseline: (String) -> Unit,
    ): Map<String, String> {
        val state = if (saved.has("canonical")) saved else prepare(saved, download())
        val backup =
            state
                .get("localEdits")
                ?.takeIf(JsonNode::isString)
                ?.stringValue()
                ?.let { Path.of(it) }
        val markdown = state.path("canonical").asString()
        if (!state.path("recovered").asBoolean(false)) {
            install(markdown, backup)
            saveBaseline(markdown)
            update(state, mapOf("recovered" to true))
        }
        val result = mapOf("documentId" to saved.path("document").asString(), "file" to file.toString())
        return if (backup == null) result else result + ("localEdits" to backup.toString())
    }

    // Persist the original backup name before moving anything. Every retry uses the same snapshot and backup.
    private fun prepare(
        saved: JsonNode,
        markdown: String,
    ): JsonNode {
        val backup =
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                file.toAbsolutePath().resolveSibling("${file.fileName}.adocs-local-${UUID.randomUUID()}.md").toString()
            } else {
                null
            }
        return update(saved, mapOf("canonical" to markdown, "localEdits" to backup))
    }

    private fun install(
        markdown: String,
        backup: Path?,
    ) {
        if (backup != null && !Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "conflict recovery needs the original local file" }
            Files.move(file, backup)
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.readString(file) != markdown) {
                throw CliFailure(
                    "local_changes",
                    "Move the new local file aside before retrying pull. Original edits: ${backup ?: "no prior file"}",
                )
            }
            return
        }
        val staged = Files.createTempFile(file.toAbsolutePath().parent, ".adocs-pull-", ".tmp")
        try {
            Files.writeString(staged, markdown)
            Files.move(staged, file)
        } finally {
            Files.deleteIfExists(staged)
        }
    }

    private fun update(
        state: JsonNode,
        fields: Map<String, Any?>,
    ): JsonNode {
        val text = Json.write(state.properties().associate { it.key to it.value } + fields)
        PrivateFiles.write(pending, text)
        return Json.read(text)
    }
}
