package xyz.robinjoon.agentdocs.application.model

import java.util.UUID

data class CurrentCallerSnapshot(
    val userId: UUID,
    val login: String,
    val connection: Connection?,
    val spaces: Set<UUID>?,
    val scope: Scope,
) {
    data class Connection(
        val id: UUID,
        val name: String,
    )

    enum class Scope { READ, COMMENT, EDIT, OWNER }
}
