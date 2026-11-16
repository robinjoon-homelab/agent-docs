package xyz.robinjoon.agentdocs.access.application.port.input

class SpaceMembershipNotFoundException(
    id: Any,
) : RuntimeException("membership not found: $id")
