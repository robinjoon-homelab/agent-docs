package xyz.robinjoon.agentdocs.workspace.application.model

data class CodeReferenceRequest(
    val repository: String? = null,
    val commit: String? = null,
    val pullRequest: Int? = null,
)
