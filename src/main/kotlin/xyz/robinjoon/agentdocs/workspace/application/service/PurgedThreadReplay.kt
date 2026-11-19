package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadNotFoundException
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission

// The opId is already bound to its writer. After purge only the document's final space can prove a scoped grant.
internal fun rejectMissingThreadReplay(
    access: Access,
    record: OperationRecord,
    needed: Permission,
): Nothing {
    if (!access.isOwner) {
        val space = record.purgedFrom?.takeIf(access::sees) ?: throw ThreadNotFoundException(record.target)
        Permissions.require(access, space, needed, "replaying a thread write")
    }
    when (record.state) {
        OperationResultState.REMOVED -> throw ResultRemovedException(record)
        OperationResultState.LEGACY -> throw LegacyOperationException(record)
        else -> throw ThreadNotFoundException(record.target)
    }
}
