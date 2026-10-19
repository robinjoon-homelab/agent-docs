package xyz.robinjoon.agentdocs.access.application.model

// scope is a space-separated hint; resources are every resource parameter sent.
data class TargetParameters(
    val scope: String?,
    val resources: List<String>,
)
