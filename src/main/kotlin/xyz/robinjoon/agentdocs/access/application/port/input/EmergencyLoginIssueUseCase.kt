package xyz.robinjoon.agentdocs.access.application.port.input

// Only a local operator adapter is wired to this port.
interface EmergencyLoginIssueUseCase {
    fun issue(): String
}
