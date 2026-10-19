package xyz.robinjoon.agentdocs.access.adapter.outbound.http

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataDocument
import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataUnavailableException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Client ids use IP literals, so the public-address check resolves nothing over the network.
class HttpClientMetadataSourceTest {
    private val client = mockk<HttpClient>()
    private val source = HttpClientMetadataSource(JsonMapper.builder().build(), client)
    private val clientId = "https://93.184.216.34/oauth/client-metadata"

    @Test
    fun `the document fields the server relies on are read`() {
        val body =
            """{"client_id":"$clientId","client_name":"Claude Code",""" +
                """"redirect_uris":["http://localhost/callback",1],"token_endpoint_auth_method":"none"}"""
        every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<InputStream>>()) } returns response(200, body)

        assertThat(
            source.fetch(clientId),
        ).isEqualTo(ClientMetadataDocument(clientId, "Claude Code", listOf("http://localhost/callback"), "none"))
    }

    @Test
    fun `an internal host, a failed answer, or a body that is not JSON is unavailable`() {
        every { client.send(any<HttpRequest>(), any<HttpResponse.BodyHandler<InputStream>>()) } returns response(404, "") andThen
            response(200, "<html>")

        assertThatThrownBy { source.fetch("https://127.0.0.1/client") }.isInstanceOf(ClientMetadataUnavailableException::class.java)
        assertThatThrownBy { source.fetch(clientId) }.isInstanceOf(ClientMetadataUnavailableException::class.java)
        assertThatThrownBy { source.fetch(clientId) }.isInstanceOf(ClientMetadataUnavailableException::class.java)
    }

    private fun response(
        status: Int,
        body: String,
    ): HttpResponse<InputStream> {
        val response = mockk<HttpResponse<InputStream>>()
        every { response.statusCode() } returns status
        every { response.body() } returns ByteArrayInputStream(body.toByteArray())
        return response
    }
}
