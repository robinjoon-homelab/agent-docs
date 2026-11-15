package xyz.robinjoon.adocs

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class Host(
    value: String,
) {
    val origin: String = value.trimEnd('/')
    val issuer: String = origin
    val clientId: String = "$origin/oauth/clients/adocs"
    val resource: String = "$origin/mcp"
    val key: String = MessageDigest.getInstance("SHA-256").digest(origin.toByteArray()).joinToString("") { "%02x".format(it) }

    init {
        val uri =
            try {
                URI(origin)
            } catch (invalid: URISyntaxException) {
                throw CliFailure("invalid_input", "host must be a valid HTTPS origin", cause = invalid)
            }
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null) { "host must be an HTTPS origin" }
        require(uri.rawQuery == null && uri.rawFragment == null && uri.path.isEmpty()) { "host must have no path or query" }
    }
}

internal fun encoded(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
