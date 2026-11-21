package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowElement(
    override val id: BlockId,
    val appearance: FlowAppearance,
    val placement: FlowPlacement,
    val text: FlowDescription,
    val behavior: FlowBehavior,
) : Block {
    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = listOf(text.label, text.value, text.desc).joinToString(" ")
}
