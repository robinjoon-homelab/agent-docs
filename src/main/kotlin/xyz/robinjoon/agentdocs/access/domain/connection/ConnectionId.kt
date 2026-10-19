package xyz.robinjoon.agentdocs.access.domain.connection

import java.util.UUID

@JvmInline
value class ConnectionId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
