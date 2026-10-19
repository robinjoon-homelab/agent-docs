package xyz.robinjoon.agentdocs.workspace.domain.document

data class Callout(
    override val id: BlockId,
    val kind: CalloutKind,
    val text: InlineText,
) : Block {
    init {
        if (text.markdown.isBlank()) {
            throw InvalidDocumentException("a callout needs text")
        }
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = text.plainText()
}
