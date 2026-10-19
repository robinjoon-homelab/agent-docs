package xyz.robinjoon.agentdocs.workspace.domain.document

data class Heading(
    override val id: BlockId,
    val level: Int,
    val text: InlineText,
) : Block {
    init {
        if (level !in MIN_LEVEL..MAX_LEVEL) {
            throw InvalidDocumentException("heading level must be between $MIN_LEVEL and $MAX_LEVEL")
        }
        if (text.markdown.isBlank()) {
            throw InvalidDocumentException("a heading needs text")
        }
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = text.plainText()

    companion object {
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 3
    }
}
