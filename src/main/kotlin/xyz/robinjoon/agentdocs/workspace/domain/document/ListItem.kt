package xyz.robinjoon.agentdocs.workspace.domain.document

// Each list item is its own block, like a line in a block editor, so a comment or an edit can target one item.
// Nesting is the depth of the item, not a parent block.
data class ListItem(
    override val id: BlockId,
    val style: ListStyle,
    val depth: Int,
    val checked: Boolean,
    val text: InlineText,
) : Block {
    init {
        if (depth !in 0..MAX_DEPTH) {
            throw InvalidDocumentException("list depth must be between 0 and $MAX_DEPTH")
        }
        if (checked && style != ListStyle.TASK) {
            throw InvalidDocumentException("only a task item can be checked")
        }
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = text.plainText()

    companion object {
        const val MAX_DEPTH = 5
    }
}
