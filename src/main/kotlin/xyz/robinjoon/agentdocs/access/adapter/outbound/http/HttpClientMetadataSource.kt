package xyz.robinjoon.agentdocs.access.adapter.outbound.http

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataDocument
import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataUnavailableException
import xyz.robinjoon.agentdocs.access.application.port.output.ClientMetadataSource
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Fetches a client id metadata document such as https://claude.ai/oauth/claude-code-client-metadata. Redirects are not
// followed, the document is capped at 64KB, and only public hosts are contacted, so a client id cannot make the server
// reach into the homelab network.
class HttpClientMetadataSource(
    private val json: ObjectMapper,
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(timeout)
            .build(),
) : ClientMetadataSource {
    override fun fetch(clientId: String): ClientMetadataDocument {
        val uri = URI(clientId)
        if (!isPublic(clientId, uri.host)) throw ClientMetadataUnavailableException(clientId, null)
        val response = send(clientId, uri)
        if (response.statusCode() != OK) throw ClientMetadataUnavailableException(clientId, null)
        val bytes = response.body().use { it.readNBytes(MAX_BYTES + 1) }
        if (bytes.size > MAX_BYTES) throw ClientMetadataUnavailableException(clientId, null)
        val node =
            try {
                json.readTree(bytes)
            } catch (exception: JacksonException) {
                throw ClientMetadataUnavailableException(clientId, exception)
            }
        return ClientMetadataDocument(
            clientId = node.text("client_id").orEmpty(),
            clientName = node.text("client_name"),
            redirectUris = node.path("redirect_uris").values().mapNotNull { it.takeIf(JsonNode::isString)?.stringValue() },
            tokenEndpointAuthMethod = node.text("token_endpoint_auth_method"),
        )
    }

    private fun send(
        clientId: String,
        uri: URI,
    ): HttpResponse<InputStream> {
        val request =
            HttpRequest
                .newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build()
        return try {
            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (exception: IOException) {
            throw ClientMetadataUnavailableException(clientId, exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ClientMetadataUnavailableException(clientId, exception)
        }
    }

    private fun isPublic(
        clientId: String,
        host: String?,
    ): Boolean {
        if (host.isNullOrBlank()) return false
        val addresses =
            try {
                InetAddress.getAllByName(host).toList()
            } catch (exception: UnknownHostException) {
                throw ClientMetadataUnavailableException(clientId, exception)
            }
        return addresses.isNotEmpty() && addresses.none(::isInternal)
    }

    // The same ranges the image download refuses: Java's address flags, plus shared address space 100.64.0.0/10 and
    // IPv6 unique local fc00::/7, which Java does not count as site-local.
    private fun isInternal(address: InetAddress): Boolean {
        val bytes = address.address
        val sharedAddressSpace = address is Inet4Address && bytes[0] == 100.toByte() && (bytes[1].toInt() and 0xC0) == 0x40
        val uniqueLocal = address is Inet6Address && (bytes[0].toInt() and 0xFE) == 0xFC
        return address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress || sharedAddressSpace || uniqueLocal
    }

    private fun JsonNode.text(field: String): String? = get(field)?.takeIf(JsonNode::isString)?.stringValue()

    private companion object {
        val timeout: Duration = Duration.ofSeconds(5)
        const val OK = 200
        const val MAX_BYTES = 64 * 1024
    }
}
