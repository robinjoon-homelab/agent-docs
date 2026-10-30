package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document

// The result of a document write. revision is the revision the write produced or replaced. amended means a person's
// continued typing replaced their latest revision instead of adding one; unchanged means nothing changed and no
// revision was added. merged means the save was based on an older revision and other writers' blocks were kept: the
// writer must take the saved blocks, or its next save would undo them. related lists the other documents the write
// changed to keep parents and descendants together, parents first.
data class DocumentWrite
    // quality-exception: 쓰기 결과 DTO라 호출자가 한 번에 받아야 할 사실을 필드로 나열한다. 묶어도 이름만 바뀐다.
    @Suppress("LongParameterList")
    constructor(
        val document: Document,
        val revision: Int,
        val changedBlocks: List<BlockId>,
        val amended: Boolean,
        val unchanged: Boolean,
        val merged: Boolean = false,
        val related: List<RelatedWrite> = emptyList(),
    )
