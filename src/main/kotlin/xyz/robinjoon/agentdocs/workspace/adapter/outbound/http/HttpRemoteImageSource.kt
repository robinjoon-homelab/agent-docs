package xyz.robinjoon.agentdocs.workspace.adapter.outbound.http

import xyz.robinjoon.agentdocs.workspace.application.model.ImageDownloadException
import xyz.robinjoon.agentdocs.workspace.application.model.RemoteImage
import xyz.robinjoon.agentdocs.workspace.application.port.output.RemoteImageSource
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
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

// Downloads an image a person pasted by address. Only public addresses are fetched, so the server cannot be used to
// reach the homelab's internal services. Redirects are followed by hand, re-checking each hop.
class HttpRemoteImageSource(
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(timeout)
            .build(),
) : RemoteImageSource {
    override fun fetch(url: String): RemoteImage? {
        var current = parse(url) ?: return null
        repeat(MAX_REDIRECTS + 1) {
            if (!isPublic(current)) return null
            val response = send(current)
            val location = response.headers().firstValue("Location").orElse(null)
            if (response.statusCode() in redirectCodes && location != null) {
                current = parse(current.resolve(location).toString()) ?: return null
            } else {
                return imageOf(current, response)
            }
        }
        return null
    }

    private fun send(uri: URI): HttpResponse<InputStream> =
        try {
            client.send(
                HttpRequest
                    .newBuilder(uri)
                    .timeout(timeout)
                    .header("Accept", "image/*")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
        } catch (exception: IOException) {
            throw ImageDownloadException(uri.toString(), exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ImageDownloadException(uri.toString(), exception)
        }

    private fun imageOf(
        uri: URI,
        response: HttpResponse<InputStream>,
    ): RemoteImage? {
        if (response.statusCode() != OK) return null
        val bytes = response.body().use { it.readNBytes((AssetContent.MAX_SIZE + 1).toInt()) }
        if (bytes.isEmpty() || bytes.size > AssetContent.MAX_SIZE) return null
        return RemoteImage(uri.toString(), response.headers().firstValue("Content-Type").orElse(null), bytes)
    }

    // URI parsing fails only with URISyntaxException, so runCatching catches nothing else.
    private fun parse(url: String): URI? =
        runCatching { URI(url) }.getOrNull()?.takeIf {
            (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() &&
                it.userInfo == null
        }

    // Every address the host resolves to must be public; one private answer is enough to refuse.
    private fun isPublic(uri: URI): Boolean {
        val addresses =
            try {
                InetAddress.getAllByName(uri.host).toList()
            } catch (exception: UnknownHostException) {
                throw ImageDownloadException(uri.toString(), exception)
            }
        return addresses.isNotEmpty() && addresses.none(::isInternal)
    }

    private fun isInternal(address: InetAddress): Boolean {
        val bytes = address.address
        val sharedAddressSpace = address is Inet4Address && bytes[0] == 100.toByte() && (bytes[1].toInt() and 0xC0) == 0x40
        val uniqueLocal = address is Inet6Address && (bytes[0].toInt() and 0xFE) == 0xFC
        return address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress || sharedAddressSpace || uniqueLocal
    }

    private companion object {
        val timeout: Duration = Duration.ofSeconds(10)
        const val MAX_REDIRECTS = 3
        const val OK = 200
        val redirectCodes = setOf(301, 302, 303, 307, 308)
    }
}
