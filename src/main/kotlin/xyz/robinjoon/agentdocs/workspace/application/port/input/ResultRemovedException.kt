package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord

class ResultRemovedException(
    val record: OperationRecord,
) : RuntimeException(
        "this operation already succeeded, but its response was removed because it contained deleted content",
    ) {
    val fix: String =
        "Do not resend the original request. Check the current document and history for the successful operation. " +
            "Only use a new opId for a separate change that is still needed."
}
