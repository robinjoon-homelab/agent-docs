package xyz.robinjoon.agentdocs.workspace.domain.document

data class Quote(
    override val id: BlockId,
    val text: InlineText,
) : Block {
    init {
        if (text.markdown.isBlank()) {
            throw InvalidDocumentException("a quote needs text")
        }
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = text.plainText()
}
