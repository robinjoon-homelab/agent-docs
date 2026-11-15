package xyz.robinjoon.agentdocs.webhook.adapter.inbound.web

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointNotFoundException
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookOwnerOnlyException
import xyz.robinjoon.agentdocs.webhook.domain.InvalidWebhookException
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.EndpointStoppedException

@RestControllerAdvice(assignableTypes = [WebhookController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class WebhookExceptionHandler {
    @ExceptionHandler(WebhookUnauthenticatedException::class)
    fun unauthenticated(exception: WebhookUnauthenticatedException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "unauthenticated", exception, "sign in with GitHub")

    @ExceptionHandler(WebhookOwnerOnlyException::class)
    fun forbidden(exception: WebhookOwnerOnlyException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, "forbidden", exception, "ask the owner to manage webhooks in the web settings")

    @ExceptionHandler(WebhookEndpointNotFoundException::class)
    fun missing(exception: WebhookEndpointNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "not_found", exception, null)

    @ExceptionHandler(InvalidWebhookException::class)
    fun invalid(exception: InvalidWebhookException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "invalid", exception, null)

    @ExceptionHandler(EndpointStoppedException::class)
    fun stopped(exception: EndpointStoppedException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "endpoint_stopped", exception, "register a new endpoint")

    private fun problem(
        status: HttpStatus,
        code: String,
        exception: RuntimeException,
        fix: String?,
    ): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(status, exception.message.orEmpty())
        problem.title = status.reasonPhrase
        problem.setProperty("code", code)
        if (fix != null) problem.setProperty("fix", fix)
        return problem
    }
}
