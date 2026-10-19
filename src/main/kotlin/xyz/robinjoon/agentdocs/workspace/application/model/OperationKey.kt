package xyz.robinjoon.agentdocs.workspace.application.model

// An operation id is unique per writer: the agent connection, or the person when no agent is involved.
data class OperationKey(
    val writer: String,
    val operationId: String,
)
