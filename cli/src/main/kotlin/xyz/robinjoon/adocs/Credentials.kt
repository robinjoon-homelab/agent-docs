package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.time.Instant

internal class Credentials(
    private val host: Host,
    private val http: Http,
) {
    private val directory = PrivateFiles.userRoot("XDG_CONFIG_HOME", ".config").resolve(host.key)
    private val file = directory.resolve("credentials.json")

    fun save(response: JsonNode) {
        val access = response.path("access_token").asString("")
        require(access.isNotBlank() && response.path("token_type").asString("").equals("Bearer", true)) { "invalid token response" }
        val saved =
            mapOf(
                "access_token" to access,
                "refresh_token" to response.path("refresh_token").asString(""),
                "expires_at" to Instant.now().epochSecond + response.path("expires_in").asLong(0),
            )
        PrivateFiles.write(file, Json.write(saved))
    }

    fun token(): String {
        System.getenv("ADOCS_TOKEN")?.takeIf(String::isNotBlank)?.let { return it }
        return PrivateFiles.locked(directory.resolve("refresh.lock")) { currentToken() }
    }

    fun logout() {
        PrivateFiles.locked(directory.resolve("refresh.lock")) { Files.deleteIfExists(file) }
    }

    private fun currentToken(): String {
        if (!Files.exists(file)) throw CliFailure("login_required", "Run adocs login --host HOST or set ADOCS_TOKEN")
        val saved = Json.read(Files.readString(file))
        if (saved.path("expires_at").asLong() > Instant.now().epochSecond + 60) return saved.path("access_token").asString()
        val refresh = saved.path("refresh_token").asString("")
        if (refresh.isBlank()) throw CliFailure("login_required", "Run adocs login --host HOST")
        val response =
            http.token(
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refresh,
                    "client_id" to host.clientId,
                    "resource" to host.resource,
                ),
            )
        save(response)
        return response.path("access_token").asString()
    }
}
