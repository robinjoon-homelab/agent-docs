package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord

// Before result snapshots were stored, successful writes only retained their identity and revision.
class LegacyOperationException(
    val record: OperationRecord,
) : RuntimeException(
        "this operation already succeeded before response snapshots were available; its original response cannot be recovered",
    ) {
    val fix: String =
        "Do not resend the original request. Check the target document and history for the successful operation. " +
            "Only use a new opId for a separate change that is still needed."
}
