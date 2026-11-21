package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowDefaults(
    val screenId: Boolean = false,
    val x: Boolean = false,
    val y: Boolean = false,
    val omitted: Set<String> = emptySet(),
) {
    companion object {
        val screenFields = setOf("entry", "frame", "frameW", "frameH")
        val elementFields = setOf("value", "look", "hasDate", "date", "pinned", "group")
    }
}
