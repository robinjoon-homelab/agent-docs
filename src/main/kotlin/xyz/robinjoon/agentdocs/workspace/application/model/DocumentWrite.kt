package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document

// The result of a document write. revision is the revision the write produced or replaced. amended means a person's
// continued typing replaced their latest revision instead of adding one; unchanged means nothing changed and no
// revision was added. merged means the save was based on an older revision and other writers' blocks were kept: the
// writer must take the saved blocks, or its next save would undo them.
data class DocumentWrite(
    val document: Document,
    val revision: Int,
    val changedBlocks: List<BlockId>,
    val amended: Boolean,
    val unchanged: Boolean,
    val merged: Boolean = false,
)
