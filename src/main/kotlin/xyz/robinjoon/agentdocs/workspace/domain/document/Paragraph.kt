package xyz.robinjoon.agentdocs.workspace.domain.document

// Markdown has no empty paragraph, so a paragraph always has text and an editor drops empty lines before saving.
data class Paragraph(
    override val id: BlockId,
    val text: InlineText,
) : Block {
    init {
        if (text.markdown.isBlank()) {
            throw InvalidDocumentException("a paragraph needs text")
        }
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = text.plainText()
}
