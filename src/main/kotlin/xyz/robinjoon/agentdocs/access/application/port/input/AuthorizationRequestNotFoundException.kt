package xyz.robinjoon.agentdocs.access.application.port.input

class AuthorizationRequestNotFoundException(
    id: Any,
) : RuntimeException("authorization request not found or expired: $id")
