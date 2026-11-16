package xyz.robinjoon.agentdocs.access.application.service

import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.application.model.Credential
import xyz.robinjoon.agentdocs.application.model.CurrentCallerSnapshot
import xyz.robinjoon.agentdocs.application.port.input.CurrentCallerUseCase

// Public proxies recheck current credentials; SSE cannot send a renewed cookie, so it only reads web sessions.
class CurrentCallerService(
    private val callers: CallerUseCase,
    private val sessions: WebSessionUseCase,
) : CurrentCallerUseCase {
    override fun current(credential: Credential): CurrentCallerSnapshot? {
        val caller =
            when (credential.kind) {
                Credential.Kind.BEARER -> callers.callerFor(credential.value())
                Credential.Kind.WEB_SESSION -> sessions.currentCaller(credential.value())
            } ?: return null
        return snapshot(caller)
    }

    private fun snapshot(caller: Caller): CurrentCallerSnapshot =
        CurrentCallerSnapshot(
            caller.userId,
            caller.login,
            caller.agent?.let { CurrentCallerSnapshot.Connection(it.connectionId, it.clientName) },
            caller.spaces?.mapValues { CurrentCallerSnapshot.Scope.valueOf(it.value.name) },
            CurrentCallerSnapshot.Scope.valueOf(caller.scope.name),
        )
}
