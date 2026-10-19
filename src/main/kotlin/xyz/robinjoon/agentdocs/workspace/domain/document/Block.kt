package xyz.robinjoon.agentdocs.workspace.domain.document

// One unit of a document. Every block type has exactly one markdown form, so people and agents describe the same
// document with the same text.
sealed interface Block {
    val id: BlockId

    fun withId(id: BlockId): Block

    // The text a reader sees, used for search and for locating comment anchors.
    fun plainText(): String
}
