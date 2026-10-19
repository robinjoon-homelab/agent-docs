package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import java.util.Collections

@ConsistentCopyVisibility
data class RevisionContent private constructor(
    val header: DocumentHeader,
    val blocks: List<Block>,
    val trashed: Boolean,
) {
    companion object {
        fun of(
            header: DocumentHeader,
            blocks: List<Block>,
            trashed: Boolean,
        ): RevisionContent = RevisionContent(header, Collections.unmodifiableList(blocks.toList()), trashed)
    }
}
