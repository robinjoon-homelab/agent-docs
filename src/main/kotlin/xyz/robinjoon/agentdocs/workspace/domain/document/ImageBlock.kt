package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId

// Refers to an uploaded asset instead of holding it, so an old revision still shows the image after the block is
// deleted. The alt text lets an agent know what the image shows without opening it.
data class ImageBlock(
    override val id: BlockId,
    val asset: AssetId,
    val alt: String,
    val caption: String,
) : Block {
    init {
        listOf(alt, caption).forEach(::requireSingleLine)
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = listOf(alt, caption).filter { it.isNotBlank() }.joinToString("\n")

    private fun requireSingleLine(text: String) {
        if (text.length > MAX_TEXT_LENGTH || text.contains('\n') || text.contains('\r')) {
            throw InvalidDocumentException("alt text and caption must be one line of at most $MAX_TEXT_LENGTH characters")
        }
    }

    companion object {
        const val MAX_TEXT_LENGTH = 500
    }
}
