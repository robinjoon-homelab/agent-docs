package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import java.time.Instant

interface OperationStore {
    fun find(key: OperationKey): OperationRecord?

    fun save(
        key: OperationKey,
        record: OperationRecord,
        at: Instant,
    )
}
