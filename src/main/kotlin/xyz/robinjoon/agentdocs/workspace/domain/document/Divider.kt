package xyz.robinjoon.agentdocs.workspace.domain.document

data class Divider(
    override val id: BlockId,
) : Block {
    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = ""
}
