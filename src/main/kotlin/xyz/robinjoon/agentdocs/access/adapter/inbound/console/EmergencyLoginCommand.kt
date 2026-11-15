package xyz.robinjoon.agentdocs.access.adapter.inbound.console

import xyz.robinjoon.agentdocs.access.application.port.input.EmergencyLoginIssueUseCase

class EmergencyLoginCommand(
    private val issue: EmergencyLoginIssueUseCase,
) {
    fun execute(): String = issue.issue()
}
