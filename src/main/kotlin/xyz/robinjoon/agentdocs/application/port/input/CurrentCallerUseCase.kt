package xyz.robinjoon.agentdocs.application.port.input

import xyz.robinjoon.agentdocs.application.model.Credential
import xyz.robinjoon.agentdocs.application.model.CurrentCallerSnapshot

interface CurrentCallerUseCase {
    fun current(credential: Credential): CurrentCallerSnapshot?
}
