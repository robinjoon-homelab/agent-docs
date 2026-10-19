package xyz.robinjoon.agentdocs.access.domain.user

import java.util.UUID

@JvmInline
value class UserId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
