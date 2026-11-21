package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowDescription(
    val label: String = "",
    val value: String = "",
    val desc: String = "",
) {
    init {
        if (label.length > 100 || value.length > 500 || desc.length > 5000) throw InvalidDocumentException("flow text is too long")
    }
}
