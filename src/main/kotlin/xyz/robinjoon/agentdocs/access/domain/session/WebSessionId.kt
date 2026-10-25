package xyz.robinjoon.agentdocs.access.domain.session

import java.util.UUID

@JvmInline
value class WebSessionId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
