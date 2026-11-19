package xyz.robinjoon.adocs

import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal class Http(
    private val host: Host,
) {
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    fun api(
        method: String,
        path: String,
        token: String,
        body: String? = null,
    ): JsonNode {
        require(path.startsWith("/api/") && !path.contains('#')) { "only /api/ paths are accepted" }
        val request = HttpRequest.newBuilder(URI(host.origin + path)).header("Authorization", "Bearer $token")
        return send(request.header("Content-Type", "application/json").method(method, publisher(body)))
    }

    fun token(form: Map<String, String>): JsonNode {
        val body = form.entries.joinToString("&") { encoded(it.key) + "=" + encoded(it.value) }
        val request = HttpRequest.newBuilder(URI(host.origin + "/oauth/token"))
        return send(request.header("Content-Type", "application/x-www-form-urlencoded").POST(publisher(body)))
    }

    fun resourceMetadata(): JsonNode = send(HttpRequest.newBuilder(URI(host.origin + "/.well-known/oauth-protected-resource")).GET())

    fun metadata(): JsonNode = send(HttpRequest.newBuilder(URI(host.origin + "/.well-known/oauth-authorization-server")).GET())

    fun upload(
        path: String,
        token: String,
        bytes: ByteArray,
        boundary: String,
    ): JsonNode {
        val request = HttpRequest.newBuilder(URI(host.origin + path)).header("Authorization", "Bearer $token")
        return send(
            request.header("Content-Type", "multipart/form-data; boundary=$boundary").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)),
        )
    }

    private fun send(builder: HttpRequest.Builder): JsonNode {
        val response = client.send(builder.timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw failure(response)
        }
        return if (response.body().isBlank()) Json.read("null") else Json.read(response.body())
    }

    private fun failure(response: HttpResponse<String>): CliFailure {
        val error =
            try {
                Json.read(response.body())
            } catch (exception: tools.jackson.core.JacksonException) {
                throw CliFailure(
                    "http_${response.statusCode()}",
                    "Inspect the server status; retry writes with the same opId",
                    response.statusCode(),
                    cause = exception,
                )
            }
        return CliFailure.fromServer(error, response.statusCode(), response.headers().firstValue("Retry-After").orElse(null))
    }

    private fun publisher(body: String?): HttpRequest.BodyPublisher =
        body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
}
