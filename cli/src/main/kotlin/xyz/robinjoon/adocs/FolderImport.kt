package xyz.robinjoon.adocs

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal class FolderImport(
    private val http: Http,
    private val token: String,
) {
    fun run(
        root: Path,
        space: String,
        tool: String,
    ): tools.jackson.databind.JsonNode {
        require(Files.isDirectory(root) && !Files.isSymbolicLink(root)) { "import root must be a real directory" }
        val files = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.sorted().toList() }
        require(
            files.none {
                Files.isSymbolicLink(it) || !it.toRealPath().startsWith(root.toRealPath())
            },
        ) { "symbolic links outside import root are not accepted" }
        val boundary = "adocs-" + UUID.randomUUID()
        val parts =
            files.flatMap { file ->
                val relative = root.relativize(file).toString().replace('\\', '/')
                listOf(field(boundary, "paths", relative), binary(boundary, file))
            }
        val bytes =
            (listOf(field(boundary, "tool", tool)) + parts + "--$boundary--\r\n".toByteArray()).fold(byteArrayOf()) { all, part ->
                all +
                    part
            }
        return http.upload("/api/spaces/${encoded(space)}/imports/folder", token, bytes, boundary)
    }

    private fun field(
        boundary: String,
        name: String,
        value: String,
    ): ByteArray = "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray()

    private fun binary(
        boundary: String,
        file: Path,
    ): ByteArray {
        val header =
            "--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"upload\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
        return header.toByteArray() + Files.readAllBytes(file) + "\r\n".toByteArray()
    }
}
