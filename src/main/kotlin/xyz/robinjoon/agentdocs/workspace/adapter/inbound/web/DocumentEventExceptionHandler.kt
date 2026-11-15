package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import java.io.IOException

@RestControllerAdvice(assignableTypes = [DocumentEventsController::class])
class DocumentEventExceptionHandler {
    @ExceptionHandler(UnauthenticatedException::class)
    fun unauthenticated(error: UnauthenticatedException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.message.orEmpty())

    @ExceptionHandler(DocumentNotFoundException::class)
    fun notFound(error: DocumentNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.message.orEmpty())

    companion object {
        // quality-exception: SSE is the final HTTP transmission boundary; terminate the connection and release its task.
        @Suppress("TooGenericExceptionCaught")
        fun transmit(
            emitter: SseEmitter,
            work: () -> Unit,
        ): Boolean {
            try {
                work()
                return true
            } catch (error: IOException) {
                emitter.completeWithError(error)
            } catch (error: RuntimeException) {
                if (error is UnauthenticatedException || error is DocumentNotFoundException) {
                    emitter.complete()
                } else {
                    emitter.completeWithError(error)
                }
            }
            return false
        }
    }
}
