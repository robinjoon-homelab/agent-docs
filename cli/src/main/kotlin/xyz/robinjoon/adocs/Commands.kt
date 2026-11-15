package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal class Commands(
    private val host: Host,
) {
    private val http = Http(host)
    private val credentials = Credentials(host, http)
    private val sync = Sync(host, http, credentials)

    fun run(
        args: Arguments,
        announce: (String) -> Unit,
    ): Any? =
        when (val command = args.argument(0)) {
            "login" -> {
                Login(host, http, credentials).run(announce)
                mapOf("loggedIn" to true)
            }

            "logout" -> {
                credentials.logout()
                mapOf("loggedOut" to true)
            }

            "pull" -> {
                sync.pull(args.argument(1), Path.of(args.argument(2)))
            }

            "push" -> {
                sync.push(Path.of(args.argument(1)))
            }

            "upload" -> {
                upload(args)
            }

            "import-folder" -> {
                FolderImport(http, credentials.token()).run(Path.of(args.argument(1)), space(args), args.required("tool"))
            }

            "api" -> {
                http.api(args.argument(1), args.argument(2), credentials.token(), body(args))
            }

            else -> {
                route(command, args)
            }
        }

    private fun route(
        command: String,
        args: Arguments,
    ): JsonNode {
        val route = CommandRegistry.routes[command] ?: throw CliFailure("unknown_command", "Run adocs help")
        val path = CommandRegistry.path(route, args) { space(args) }
        return http.api(route.method, path, credentials.token(), body(args))
    }

    private fun body(args: Arguments): String? = args.option("body")?.let { Files.readString(Path.of(it)) }

    private fun space(args: Arguments): String {
        args.option("space")?.let { return it }
        val process = ProcessBuilder("git", "remote", "get-url", "origin").redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val remote = process.inputStream.bufferedReader().use { it.readText().trim() }
        require(process.waitFor() == 0 && remote.isNotBlank()) { "provide --space or run inside a repository with origin" }
        val resolved = http.api("POST", "/api/spaces/resolve", credentials.token(), Json.write(mapOf("remote" to remote)))
        return resolved.path("slug").asString("").takeIf(String::isNotBlank)
            ?: throw CliFailure("space_not_found", "Provide --space SLUG")
    }

    private fun upload(args: Arguments): JsonNode {
        val file = Path.of(args.argument(1))
        val boundary = "adocs-" + UUID.randomUUID()
        val name = file.fileName.toString().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val prefix =
            "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n"
        val bytes = prefix.toByteArray() + Files.readAllBytes(file) + "\r\n--$boundary--\r\n".toByteArray()
        return http.upload("/api/spaces/${encoded(space(args))}/assets", credentials.token(), bytes, boundary)
    }
}
