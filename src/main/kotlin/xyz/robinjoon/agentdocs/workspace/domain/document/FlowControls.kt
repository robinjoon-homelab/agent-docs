package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowControls(
    val pinned: Boolean,
    val group: String?,
    val annotated: Boolean,
)
