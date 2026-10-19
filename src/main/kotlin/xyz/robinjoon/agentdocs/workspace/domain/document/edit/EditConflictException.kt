package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import java.util.Collections

class EditConflictException(
    conflicts: List<BlockConflict>,
) : RuntimeException("blocks changed since they were read: ${conflicts.joinToString { it.blockId.value }}") {
    val conflicts: List<BlockConflict> = Collections.unmodifiableList(conflicts.toList())
}
