package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import xyz.robinjoon.agentdocs.application.model.Credential
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentEventsUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// A finite connection bounds retained credentials; browser reconnect always starts with a fresh invalidation.
internal class DocumentEventStream(
    private val emitter: SseEmitter,
    private val snapshots: DocumentEventsUseCase,
    private val callers: DocumentEventCaller,
    private val credential: Credential,
    private val document: DocumentId,
) {
    private var previous: DocumentEventSnapshot? = null
    private var ticks = 0
    private var closed = false
    private var task: ScheduledFuture<*>? = null

    @Synchronized
    fun start(scheduler: ScheduledExecutorService) {
        emitter.onCompletion(::close)
        emitter.onTimeout {
            close()
            emitter.complete()
        }
        emitter.onError { close() }
        task = scheduler.scheduleWithFixedDelay(::poll, 0, 1, TimeUnit.SECONDS)
    }

    @Synchronized
    private fun poll() {
        if (closed) return
        val sent =
            DocumentEventExceptionHandler.transmit(emitter) {
                val current = snapshots.snapshot(callers.current(credential), document)
                if (current != previous) {
                    emitter.send(SseEmitter.event().name("invalidate").data("{}"))
                } else if (++ticks % 15 == 0) {
                    emitter.send(SseEmitter.event().comment("heartbeat"))
                }
                previous = current
            }
        if (!sent) close()
    }

    @Synchronized
    private fun close() {
        closed = true
        task?.cancel(false)
        task = null
    }
}
