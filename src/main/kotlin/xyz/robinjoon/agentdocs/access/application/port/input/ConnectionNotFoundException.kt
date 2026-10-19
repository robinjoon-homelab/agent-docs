package xyz.robinjoon.agentdocs.access.application.port.input

class ConnectionNotFoundException(
    id: Any,
) : RuntimeException("connection not found: $id")
