package xyz.robinjoon.agentdocs.workspace.application.model

data class DesignValidationWarning(
    val designId: String,
    val code: String,
    val detail: String,
)
