package xyz.robinjoon.agentdocs.webhook.adapter.outbound.http

import xyz.robinjoon.agentdocs.webhook.application.model.OutgoingWebhook
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookGateway
import xyz.robinjoon.agentdocs.webhook.domain.delivery.AttemptResult
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Posts one signed request over HTTP/1.1, so a plain http receiver never sees an h2c upgrade. Redirects are never
// followed and the whole exchange, body included, ends after 15 seconds. The response body is discarded and never
// stored, and failures are reported as short codes, never with the address or the secret.
class HttpWebhookGateway(
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(timeout)
            .build(),
    private val resolve: (String) -> List<InetAddress> = { host -> InetAddress.getAllByName(host).toList() },
) : WebhookGateway {
    override fun send(request: OutgoingWebhook): AttemptResult {
        val addresses =
            try {
                resolve(request.url.host)
            } catch (exception: UnknownHostException) {
                return unanswered(exception)
            }
        if (addresses.isEmpty()) return AttemptResult.Unanswered(UNKNOWN_HOST)
        if (addresses.any(WebhookAddressPolicy::refuses)) return AttemptResult.Unanswered(REFUSED_ADDRESS)
        return exchange(build(request))
    }

    private fun build(request: OutgoingWebhook): HttpRequest {
        val timestamp = request.attemptAt.epochSecond
        val signature = StandardWebhookSignature.sign(request.secret.key(), request.messageId, timestamp, request.payload)
        return HttpRequest
            .newBuilder(request.url)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("webhook-id", request.messageId)
            .header("webhook-timestamp", timestamp.toString())
            .header("webhook-signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(request.payload, Charsets.UTF_8))
            .build()
    }

    private fun exchange(request: HttpRequest): AttemptResult {
        val pending = client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
        return try {
            val response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            AttemptResult.Answered(response.statusCode(), retryAfter(response))
        } catch (exception: TimeoutException) {
            pending.cancel(true)
            unanswered(exception)
        } catch (exception: ExecutionException) {
            unanswered(exception.cause ?: exception)
        } catch (exception: InterruptedException) {
            // Shutting down: the delivery keeps its lease and is sent again after it ends.
            pending.cancel(true)
            throw exception
        }
    }

    private fun unanswered(cause: Throwable): AttemptResult =
        AttemptResult.Unanswered(
            when (cause) {
                is UnknownHostException -> UNKNOWN_HOST
                is TimeoutException, is HttpTimeoutException -> TIMED_OUT
                else -> CONNECTION_FAILED
            },
        )

    // Only the delta-seconds form; an HTTP date is ignored and the regular schedule applies.
    private fun retryAfter(response: HttpResponse<*>): Duration? =
        response
            .headers()
            .firstValue("retry-after")
            .map {
                it
                    .trim()
                    .toLongOrNull()
                    ?.takeIf { seconds -> seconds >= 0 }
                    ?.let(Duration::ofSeconds)
            }.orElse(null)

    private companion object {
        val timeout: Duration = Duration.ofSeconds(15)
        const val USER_AGENT = "agent-docs-webhook"
        const val UNKNOWN_HOST = "unknown_host"
        const val REFUSED_ADDRESS = "refused_address"
        const val TIMED_OUT = "timeout"
        const val CONNECTION_FAILED = "connection_failed"
    }
}
