package xyz.robinjoon.agentdocs.access.domain.membership

import java.util.UUID

@JvmInline
value class SpaceMembershipId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()
}
