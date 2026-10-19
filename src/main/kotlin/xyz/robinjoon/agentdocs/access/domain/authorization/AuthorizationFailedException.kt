package xyz.robinjoon.agentdocs.access.domain.authorization

// An OAuth request that fails with a standard error code such as invalid_grant, which the token endpoint returns as is.
class AuthorizationFailedException(
    val error: String,
    description: String,
) : RuntimeException(description)
