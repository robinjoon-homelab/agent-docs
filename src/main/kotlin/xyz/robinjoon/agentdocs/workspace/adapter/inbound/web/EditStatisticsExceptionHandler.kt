package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException

@RestControllerAdvice(assignableTypes = [EditStatisticsController::class])
class EditStatisticsExceptionHandler {
    @ExceptionHandler(DocumentNotFoundException::class)
    fun missing(exception: DocumentNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "not_found", exception)

    @ExceptionHandler(UnauthenticatedException::class)
    fun unauthenticated(exception: UnauthenticatedException): ProblemDetail = problem(HttpStatus.UNAUTHORIZED, "unauthenticated", exception)

    @ExceptionHandler(PermissionDeniedException::class)
    fun forbidden(exception: PermissionDeniedException): ProblemDetail = problem(HttpStatus.FORBIDDEN, "forbidden", exception)

    @ExceptionHandler(IllegalArgumentException::class, InvalidRequestException::class)
    fun invalid(exception: RuntimeException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "invalid", exception)

    companion object {
        private val logger = LoggerFactory.getLogger(EditStatisticsExceptionHandler::class.java)

        // quality-exception: HTTP terminal boundary isolates optional statistics after the document already committed.
        @Suppress("TooGenericExceptionCaught")
        fun afterWrite(action: () -> Unit): Boolean =
            try {
                action()
                true
            } catch (exception: RuntimeException) {
                if (interrupted(exception)) Thread.currentThread().interrupt()
                if (Thread.currentThread().isInterrupted) throw exception
                logger.warn("Optional edit statistics were not recorded after a committed write: {}", exception.javaClass.simpleName)
                false
            }

        private fun interrupted(exception: Throwable): Boolean = generateSequence(exception) { it.cause }.any { it is InterruptedException }
    }

    private fun problem(
        status: HttpStatus,
        code: String,
        exception: RuntimeException,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, exception.message.orEmpty()).apply {
            setProperty("code", code)
            setProperty("fix", "Check the document access and input timestamps")
        }
}
