package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.UsageReport
import xyz.robinjoon.agentdocs.workspace.domain.Access

interface UsageUseCase {
    // Owner only. Covers the last four weeks.
    fun report(access: Access): UsageReport
}
