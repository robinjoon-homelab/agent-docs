package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document

// The document and response facts captured by one write, including related writes and block marks for replay.
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
        val marks: List<BlockMark> = emptyList(),
        val space: SpaceReference? = null,
    )
