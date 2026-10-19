package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.UsageFacts
import java.time.Instant

interface UsageCatalog {
    fun since(start: Instant): UsageFacts
}
