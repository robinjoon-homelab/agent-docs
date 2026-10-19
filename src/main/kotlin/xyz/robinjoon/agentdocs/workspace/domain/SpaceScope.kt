package xyz.robinjoon.agentdocs.workspace.domain

// Which spaces an actor may see. The owner sees every space. An agent sees only the spaces its connection lists,
// and a space created later never joins an existing connection.
sealed interface SpaceScope {
    fun includes(space: SpaceId): Boolean

    data object AllSpaces : SpaceScope {
        override fun includes(space: SpaceId): Boolean = true
    }

    data class Listed(
        val spaces: Set<SpaceId>,
    ) : SpaceScope {
        override fun includes(space: SpaceId): Boolean = space in spaces
    }
}
