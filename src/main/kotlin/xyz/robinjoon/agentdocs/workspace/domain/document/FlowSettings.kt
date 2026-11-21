package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowSettings(
    override val id: BlockId,
    val frame: String = "phone",
    val idPrefix: String = "M",
    val nextScreen: Int = 2,
) : Block {
    init {
        if (frame !in FlowFrame.presets || !Regex("[A-Z0-9]{0,4}").matches(idPrefix) || nextScreen < 1) {
            throw InvalidDocumentException("invalid flow settings")
        }
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = ""
}
