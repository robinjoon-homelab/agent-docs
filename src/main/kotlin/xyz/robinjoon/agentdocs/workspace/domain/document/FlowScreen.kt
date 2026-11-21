package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowScreen(
    override val id: BlockId,
    val screenId: String,
    val name: String = "새 화면",
    val entry: String = "",
    val frame: FlowFrame = FlowFrame(),
    val desc: String = "",
) : Block {
    init {
        if (!Regex("[A-Z0-9]{0,4}-[0-9]{2,}").matches(screenId) || name.length > 100 || entry.length > 200 ||
            desc.length > MAX_DESCRIPTION
        ) {
            throw InvalidDocumentException("invalid flow screen")
        }
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    companion object {
        // Note pages keep long specification text in the screen description.
        const val MAX_DESCRIPTION = 20_000
    }

    override fun plainText(): String = "$name $entry $desc"
}
