package xyz.robinjoon.agentdocs.access.domain.authorization

import java.util.UUID

@JvmInline
value class AuthorizationRequestId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
