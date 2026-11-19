package xyz.robinjoon.agentdocs.webhook.adapter.outbound.http

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.webhook.application.model.OutgoingWebhook
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

// One adapter against a fake receiver on the loopback interface, which the owner may also use for a homelab receiver.
class HttpWebhookGatewayTest {
    private val received = ConcurrentHashMap<String, String>()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val secret = WebhookSecret.parse("whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw")
    private val attemptAt = Instant.ofEpochSecond(1_614_265_330)

    init {
        server.createContext("/") { exchange ->
            exchange.requestHeaders.forEach { (name, values) -> received[name.lowercase()] = values.joinToString(",") }
            received["method"] = exchange.requestMethod
            received["body"] = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val path = exchange.requestURI.path
            if (path == "/busy") exchange.responseHeaders.add("Retry-After", "120")
            if (path == "/moved") exchange.responseHeaders.add("Location", "http://169.254.169.254/")
            exchange.sendResponseHeaders(statusOf(path), -1)
            exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    @Test
    fun `the request carries the Standard Webhooks headers and the body exactly as planned`() {
        val result = HttpWebhookGateway().send(request("/ok"))

        assertThat(result).isEqualTo(AttemptResult.Answered(204, null))
        assertThat(received["method"]).isEqualTo("POST")
        assertThat(received["body"]).isEqualTo(PAYLOAD)
        assertThat(received["content-type"]).isEqualTo("application/json")
        assertThat(received["webhook-id"]).isEqualTo(MESSAGE_ID)
        assertThat(received["webhook-timestamp"]).isEqualTo("1614265330")
        assertThat(received["webhook-signature"]).isEqualTo("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=")
        assertThat(received.values.joinToString()).doesNotContain(secret.serialized.removePrefix("whsec_"))
    }

    @Test
    fun `a key rotated during request preparation signs the request that follows`() {
        val rotated = WebhookSecret.parse("whsec_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        var current = secret
        val gateway =
            HttpWebhookGateway(resolve = {
                current = rotated
                listOf(InetAddress.getLoopbackAddress())
            })

        val result = gateway.send(request("/ok").copy(signingSecret = { current }))

        assertThat(result).isEqualTo(AttemptResult.Answered(204, null))
        assertThat(received["webhook-signature"])
            .isEqualTo(StandardWebhookSignature.sign(rotated.key(), MESSAGE_ID, attemptAt.epochSecond, PAYLOAD))
    }

    @Test
    fun `answers are returned as they came, redirects are not followed, and Retry-After seconds are read`() {
        val gateway = HttpWebhookGateway()

        assertThat(gateway.send(request("/gone"))).isEqualTo(AttemptResult.Answered(410, null))
        assertThat(gateway.send(request("/moved"))).isEqualTo(AttemptResult.Answered(302, null))
        assertThat(gateway.send(request("/busy"))).isEqualTo(AttemptResult.Answered(503, Duration.ofSeconds(120)))
    }

    @Test
    fun `a host resolving to link-local or unspecified addresses is refused before connecting`() {
        val metadata = HttpWebhookGateway(resolve = { listOf(InetAddress.getByName("169.254.169.254")) })
        val unspecified = HttpWebhookGateway(resolve = { listOf(InetAddress.getLoopbackAddress(), InetAddress.getByName("0.0.0.0")) })

        assertThat(metadata.send(request("/ok"))).isEqualTo(AttemptResult.Unanswered("refused_address"))
        assertThat(unspecified.send(request("/ok"))).isEqualTo(AttemptResult.Unanswered("refused_address"))
        assertThat(received).isEmpty()
    }

    @Test
    fun `an unknown host or a closed port is a short error code`() {
        val unknown = HttpWebhookGateway(resolve = { throw UnknownHostException("hooks.invalid") })
        val closed = request("/ok").copy(url = URI.create("http://127.0.0.1:${closedPort()}/hook"))

        assertThat(unknown.send(request("/ok"))).isEqualTo(AttemptResult.Unanswered("unknown_host"))
        assertThat(HttpWebhookGateway().send(closed)).isEqualTo(AttemptResult.Unanswered("connection_failed"))
    }

    @Test
    fun `the signature matches the reference implementation's test vector`() {
        val key = Base64.getDecoder().decode("MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw")

        val signature = StandardWebhookSignature.sign(key, MESSAGE_ID, 1_614_265_330, PAYLOAD)

        assertThat(signature).isEqualTo("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=")
    }

    private fun request(path: String): OutgoingWebhook =
        OutgoingWebhook(URI.create("http://127.0.0.1:${server.address.port}$path"), MESSAGE_ID, attemptAt, PAYLOAD) { secret }

    private fun statusOf(path: String): Int =
        when (path) {
            "/gone" -> 410
            "/moved" -> 302
            "/busy" -> 503
            else -> 204
        }

    private fun closedPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

    private companion object {
        // The Standard Webhooks reference implementation's test vector.
        const val MESSAGE_ID = "msg_p5jXN8AQM9LWM0D4loKWxJek"
        const val PAYLOAD = """{"test": 2432232314}"""
    }
}
