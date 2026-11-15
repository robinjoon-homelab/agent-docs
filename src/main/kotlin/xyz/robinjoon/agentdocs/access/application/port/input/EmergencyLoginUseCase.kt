package xyz.robinjoon.agentdocs.access.application.port.input

interface EmergencyLoginUseCase {
    fun consume(token: String): String
}
