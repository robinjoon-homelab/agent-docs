package xyz.robinjoon.agentdocs.workspace.domain

import java.util.Collections

// Which spaces an actor may see. The owner sees every space. An agent sees only the spaces its connection lists,
// and a space created later never joins an existing connection.
sealed interface SpaceScope {
    fun includes(space: SpaceId): Boolean

    data object AllSpaces : SpaceScope {
        override fun includes(space: SpaceId): Boolean = true
    }

    class Listed(
        grants: Map<SpaceId, Permission>,
    ) : SpaceScope {
        val grants: Map<SpaceId, Permission> = Collections.unmodifiableMap(LinkedHashMap(grants))
        val spaces: Set<SpaceId> get() = grants.keys

        init {
            require(Permission.OWNER !in this.grants.values) { "listed spaces cannot grant OWNER" }
        }

        override fun includes(space: SpaceId): Boolean = space in spaces

        override fun equals(other: Any?): Boolean = this === other || (other is Listed && grants == other.grants)

        override fun hashCode(): Int = grants.hashCode()
    }
}
