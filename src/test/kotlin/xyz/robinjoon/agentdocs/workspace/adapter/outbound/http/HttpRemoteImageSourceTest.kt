package xyz.robinjoon.agentdocs.workspace.adapter.outbound.http

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.ImageDownloadException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Addresses are IP literals, so no lookup or connection leaves the machine.
class HttpRemoteImageSourceTest {
    private val client = mockk<HttpClient>()
    private val source = HttpRemoteImageSource(client)

    @Test
    fun `internal addresses are never fetched`() {
        val internal =
            listOf(
                "http://127.0.0.1/a.png",
                "http://10.0.0.5/a.png",
                "http://192.168.1.2/a.png",
                "http://100.64.0.1/a.png",
                "http://[::1]/a.png",
                "file:///etc/passwd",
            )

        internal.forEach { assertThat(source.fetch(it)).isNull() }
    }

    @Test
    fun `a public image is downloaded and a redirect to an internal address is refused`() {
        every {
            client.send(match<HttpRequest> { it.uri() == URI("https://93.184.216.34/a.png") }, any<HttpResponse.BodyHandler<InputStream>>())
        } returns
            response(200, mapOf("Content-Type" to "image/png"), byteArrayOf(1, 2, 3))
        every {
            client.send(match<HttpRequest> { it.uri() == URI("https://93.184.216.34/moved") }, any<HttpResponse.BodyHandler<InputStream>>())
        } returns
            response(302, mapOf("Location" to "http://127.0.0.1/secret.png"), ByteArray(0))

        val image = requireNotNull(source.fetch("https://93.184.216.34/a.png"))

        assertThat(image.bytes).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(image.declaredType).isEqualTo("image/png")
        assertThat(source.fetch("https://93.184.216.34/moved")).isNull()
    }

    @Test
    fun `a failed connection is reported with its cause`() {
        every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<InputStream>>()) } throws IOException("reset")

        assertThatThrownBy { source.fetch("https://93.184.216.34/a.png") }
            .isInstanceOf(ImageDownloadException::class.java)
            .hasCauseInstanceOf(IOException::class.java)
    }

    private fun response(
        status: Int,
        headers: Map<String, String>,
        body: ByteArray,
    ): HttpResponse<InputStream> {
        val response = mockk<HttpResponse<InputStream>>()
        every { response.statusCode() } returns status
        every { response.headers() } returns HttpHeaders.of(headers.mapValues { listOf(it.value) }) { _, _ -> true }
        every { response.body() } returns ByteArrayInputStream(body)
        every { response.uri() } returns URI.create("https://93.184.216.34/")
        return response
    }
}
