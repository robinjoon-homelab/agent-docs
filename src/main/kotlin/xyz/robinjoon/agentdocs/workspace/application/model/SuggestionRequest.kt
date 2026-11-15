package xyz.robinjoon.agentdocs.workspace.application.model

data class SuggestionRequest(
    val anchor: AnchorRequest,
    val hash: String?,
    val before: String,
    val after: String,
)
