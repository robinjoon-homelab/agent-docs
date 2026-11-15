package xyz.robinjoon.agentdocs.webhook.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.EndpointStop
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.EndpointStoppedException
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.StopReason
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.Subscription
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpoint
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookSecret
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookUrl
import java.time.Instant
import java.util.Base64
import java.util.UUID

class WebhookEndpointTest {
    private val at = Instant.parse("2026-11-16T03:00:00Z")
    private val secret = WebhookSecret.of(ByteArray(32) { it.toByte() })

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://hooks.example.com/agent-docs?team=1",
            "http://192.168.0.20:8080/hook",
            "http://10.0.0.5/hook",
            "http://receiver.homelab.svc.cluster.local/hook",
            "http://localhost:9000/hook",
            "http://[fd12::5]/hook",
        ],
    )
    fun `the owner may point a webhook at public or homelab addresses`(url: String) {
        assertThat(WebhookUrl.of(" $url ").toString()).isEqualTo(url)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://user:pass@hooks.example.com/hook",
            "https://token@hooks.example.com/hook",
            "ftp://hooks.example.com/hook",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "HTTPS://hooks.example.com/hook",
            "https://hooks.example.com/hook#part",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0:8080/",
            "http://224.0.0.1/",
            "http://255.255.255.255/",
            "http://[fe80::1]/",
            "http://[::]/",
            "http://[ff02::1]/",
            "http://[::ffff:169.254.169.254]/",
            "/relative/path",
            "",
        ],
    )
    fun `addresses with credentials, other schemes, fragments, or unusable literals are refused`(url: String) {
        assertThatThrownBy { WebhookUrl.of(url) }.isInstanceOf(InvalidWebhookException::class.java)
    }

    @Test
    fun `a secret is written whsec_ and base64, parses back, and never shows in toString`() {
        val parsed = WebhookSecret.parse(secret.serialized)

        assertThat(secret.serialized).isEqualTo("whsec_" + Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))
        assertThat(parsed).isEqualTo(secret)
        assertThat(parsed.toString()).doesNotContain(secret.serialized.removePrefix("whsec_"))
        assertThat(endpoint().toString()).doesNotContain(secret.serialized.removePrefix("whsec_"))
    }

    @Test
    fun `a secret is 24 to 64 bytes behind the whsec_ prefix`() {
        assertThatThrownBy { WebhookSecret.of(ByteArray(23)) }.isInstanceOf(InvalidWebhookException::class.java)
        assertThatThrownBy { WebhookSecret.of(ByteArray(65)) }.isInstanceOf(InvalidWebhookException::class.java)
        assertThatThrownBy { WebhookSecret.parse(Base64.getEncoder().encodeToString(ByteArray(32))) }
            .isInstanceOf(InvalidWebhookException::class.java)
        assertThatThrownBy { WebhookSecret.parse("whsec_not base64!") }.isInstanceOf(InvalidWebhookException::class.java)
    }

    @Test
    fun `an endpoint takes subscribed events from its start until it stops`() {
        val endpoint = endpoint()
        val stopped = endpoint.stop(StopReason.OWNER, at.plusSeconds(60))

        assertThat(endpoint.wants(WebhookEventType.COMMENT_CREATED, at)).isTrue()
        assertThat(endpoint.wants(WebhookEventType.COMMENT_CREATED, at.minusMillis(1))).isFalse()
        assertThat(endpoint.wants(WebhookEventType.DOCUMENT_CHANGED, at.plusSeconds(1))).isFalse()
        assertThat(stopped.wants(WebhookEventType.COMMENT_CREATED, at.plusSeconds(1))).isFalse()
    }

    @Test
    fun `stopping twice keeps the first stop and a stopped endpoint keeps its secret`() {
        val stopped = endpoint().stop(StopReason.OWNER, at.plusSeconds(60))
        val again = stopped.stop(StopReason.GONE, at.plusSeconds(120))

        assertThat(again.timeline.stop).isEqualTo(EndpointStop(at.plusSeconds(60), StopReason.OWNER))
        assertThatThrownBy { stopped.rotateSecret(WebhookSecret.of(ByteArray(32)), at.plusSeconds(180)) }
            .isInstanceOf(EndpointStoppedException::class.java)
    }

    @Test
    fun `replacing the secret records when it changed`() {
        val replacement = WebhookSecret.of(ByteArray(48) { 7 })

        val rotated = endpoint().rotateSecret(replacement, at.plusSeconds(30))

        assertThat(rotated.secret).isEqualTo(replacement)
        assertThat(rotated.timeline.secretRotatedAt).isEqualTo(at.plusSeconds(30))
    }

    @Test
    fun `an endpoint subscribes to at least one known event`() {
        assertThatThrownBy { Subscription.of(WebhookUrl.of("https://hooks.example.com"), emptySet()) }
            .isInstanceOf(InvalidWebhookException::class.java)
        assertThatThrownBy { WebhookEventType.of("document.revised") }.isInstanceOf(InvalidWebhookException::class.java)
    }

    private fun endpoint(): WebhookEndpoint =
        WebhookEndpoint.register(
            WebhookEndpointId(UUID.fromString("44444444-4444-4444-8444-444444444444")),
            Subscription.of(WebhookUrl.of("https://hooks.example.com/hook"), setOf(WebhookEventType.COMMENT_CREATED)),
            secret,
            at,
        )
}
