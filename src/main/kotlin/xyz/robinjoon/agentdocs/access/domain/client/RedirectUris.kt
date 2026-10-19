package xyz.robinjoon.agentdocs.access.domain.client

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import java.net.URI

// Redirect URIs match exactly, except that a loopback http URI matches on any port: native clients such as Claude
// Code and Codex listen on a port the operating system picks (RFC 8252 section 7.3).
object RedirectUris {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    fun requireValid(uri: String): String {
        val parsed = parse(uri) ?: throw InvalidAccessException("redirect URI is not a URI: $uri")
        val loopback = parsed.scheme == "http" && parsed.host in loopbackHosts
        if (parsed.fragment != null || (parsed.scheme != "https" && !loopback) || parsed.host.isNullOrBlank()) {
            throw InvalidAccessException("a redirect URI must be https or a loopback http URI without a fragment: $uri")
        }
        return uri
    }

    fun matches(
        registered: String,
        requested: String,
    ): Boolean {
        if (registered == requested) return true
        val expected = parse(registered) ?: return false
        val actual = parse(requested) ?: return false
        val loopback = expected.scheme == "http" && expected.host in loopbackHosts
        return loopback && actual.scheme == expected.scheme && actual.host == expected.host && actual.rawPath == expected.rawPath &&
            actual.rawQuery == expected.rawQuery && actual.fragment == null
    }

    // Parsing fails only with URISyntaxException, the one failure runCatching has to cover here.
    private fun parse(uri: String): URI? = runCatching { URI(uri) }.getOrNull()
}
