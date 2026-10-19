package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.port.input.AuthorizationRequestNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.ConnectionNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationFailedException

// Applies only to the access controllers. OAuth endpoints answer in the OAuth error shape; the rest use problem
// details with a code the web app and agents can branch on.
@RestControllerAdvice(
    assignableTypes = [
        AuthorizationController::class, TokenController::class, RegistrationController::class, ConnectionController::class,
        MeController::class, OAuthMetadataController::class,
    ],
)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccessExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(OAuthException::class)
    fun handleOAuth(exception: OAuthException): ResponseEntity<Map<String, String>> =
        ResponseEntity
            .status(exception.status)
            .cacheControl(CacheControl.noStore())
            .body(mapOf("error" to exception.error, "error_description" to exception.message.orEmpty()))

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(exception: UnauthenticatedException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "unauthenticated", exception, "sign in with GitHub")

    @ExceptionHandler(OwnerOnlyException::class)
    fun handleOwnerOnly(exception: OwnerOnlyException): ProblemDetail = problem(HttpStatus.FORBIDDEN, "forbidden", exception, null)

    @ExceptionHandler(ConnectionNotFoundException::class, AuthorizationRequestNotFoundException::class)
    fun handleNotFound(exception: RuntimeException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "not_found", exception, null)

    @ExceptionHandler(InvalidAccessException::class)
    fun handleInvalid(exception: InvalidAccessException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "invalid", exception, null)

    // The request was already approved, denied, or expired while the screen was open.
    @ExceptionHandler(AuthorizationFailedException::class)
    fun handleAuthorizationFailed(exception: AuthorizationFailedException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "conflict", exception, "start the connection again from the client")

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
