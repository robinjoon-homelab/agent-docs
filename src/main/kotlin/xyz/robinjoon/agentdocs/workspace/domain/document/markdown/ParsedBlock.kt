package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.Block

// A block read from markdown. declaredHash is the hash the writer annotated when the reader last saw the block, or
// null for a block written without an annotation.
data class ParsedBlock(
    val block: Block,
    val declaredHash: String?,
)
