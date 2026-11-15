package xyz.robinjoon.adocs

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class Login(
    private val host: Host,
    private val http: Http,
    private val credentials: Credentials,
) {
    fun run(announce: (String) -> Unit) {
        validateMetadata()
        val state = secret()
        val verifier = secret()
        val code = CompletableFuture<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val redirect = "http://127.0.0.1:${server.address.port}/callback"
        server.createContext("/callback") { exchange -> callback(exchange, state, code) }
        server.start()
        try {
            val url = authorizationUrl(redirect, state, verifier)
            open(url, announce)
            val response =
                http.token(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code.get(180, TimeUnit.SECONDS),
                        "client_id" to host.clientId,
                        "redirect_uri" to redirect,
                        "code_verifier" to verifier,
                        "resource" to host.resource,
                    ),
                )
            credentials.save(response)
        } finally {
            server.stop(0)
        }
    }

    private fun validateMetadata() {
        val resource = http.resourceMetadata()
        require(resource.path("resource").asString() == host.resource) { "resource mismatch" }
        require(resource.path("authorization_servers").values().any { it.asString() == host.issuer }) { "resource issuer mismatch" }
        val metadata = http.metadata()
        require(metadata.path("client_id_metadata_document_supported").asBoolean()) { "CIMD support required" }
        require(metadata.path("issuer").asString() == host.issuer) { "issuer mismatch" }
        require(
            metadata.path("authorization_endpoint").asString() == host.origin + "/oauth/authorize",
        ) { "authorization endpoint mismatch" }
        require(metadata.path("token_endpoint").asString() == host.origin + "/oauth/token") { "token endpoint mismatch" }
        require(metadata.path("code_challenge_methods_supported").values().any { it.asString() == "S256" }) { "S256 required" }
    }

    private fun authorizationUrl(
        redirect: String,
        state: String,
        verifier: String,
    ): String {
        val challenge =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            )
        val form =
            mapOf(
                "response_type" to "code",
                "client_id" to host.clientId,
                "redirect_uri" to redirect,
                "scope" to "read comment edit",
                "state" to state,
                "resource" to host.resource,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
            )
        return host.origin + "/oauth/authorize?" + form.entries.joinToString("&") { encoded(it.key) + "=" + encoded(it.value) }
    }

    private fun callback(
        exchange: HttpExchange,
        state: String,
        code: CompletableFuture<String>,
    ) {
        val values = query(exchange.requestURI.rawQuery.orEmpty())
        val valid =
            exchange.requestMethod == "GET" && exchange.requestURI.path == "/callback" &&
                values["state"] == listOf(state) && values["iss"] == listOf(host.issuer) && values["code"]?.size == 1 &&
                !values["code"]?.single().isNullOrBlank()
        val message = if (valid) "Authorization received. Return to adocs." else "Invalid authorization response."
        exchange.sendResponseHeaders(if (valid) 200 else 400, message.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(message.toByteArray()) }
        if (valid) code.complete(requireNotNull(values["code"]?.single()))
    }

    private fun query(raw: String): Map<String, List<String>> =
        raw
            .split('&')
            .filter(String::isNotBlank)
            .map {
                URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
            }.groupBy({ it.first }, { it.second })

    private fun open(
        url: String,
        announce: (String) -> Unit,
    ) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            announce(url)
            return
        }
        try {
            Desktop.getDesktop().browse(URI(url))
        } catch (exception: IOException) {
            announce("Browser launch failed (${exception.javaClass.simpleName}). Open this address: $url")
        }
    }

    private fun secret(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
}
