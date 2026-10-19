package xyz.robinjoon.agentdocs.access.application.port.input

// Connections are managed by the owner only, and never by an agent.
class OwnerOnlyException(
    action: String,
) : RuntimeException("$action is for the owner only")
