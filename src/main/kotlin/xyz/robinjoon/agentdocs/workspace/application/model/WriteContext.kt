package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource

// What every write carries: who writes with which rights, through which path, an optional operation id that makes a
// retried request apply only once, and an optional one-line change summary.
data class WriteContext(
    val access: Access,
    val source: ChangeSource,
    val operationId: String?,
    val summary: String?,
) {
    init {
        if (operationId != null && !operationPattern.matches(operationId)) {
            throw InvalidRequestException("opId must be 1 to 100 letters, digits, '-', '_', '.', or ':'")
        }
    }

    private companion object {
        val operationPattern = Regex("[A-Za-z0-9._:-]{1,100}")
    }
}
