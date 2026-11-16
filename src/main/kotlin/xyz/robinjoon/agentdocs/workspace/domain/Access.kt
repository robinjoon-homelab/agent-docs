package xyz.robinjoon.agentdocs.workspace.domain

// What one request may do. Use cases receive it with every call and answer a space outside the scope exactly like a
// missing one, so neither a person nor an agent can learn that it exists.
data class Access(
    val actor: Actor,
    val spaces: SpaceScope,
    val permission: Permission,
) {
    init {
        require((spaces == SpaceScope.AllSpaces) == (permission == Permission.OWNER && !actor.isAgent)) {
            "only a person owner sees all spaces"
        }
        require(permission != Permission.OWNER || !actor.isAgent) { "an agent cannot hold OWNER" }
    }

    val isOwner: Boolean get() = permission == Permission.OWNER

    fun sees(space: SpaceId): Boolean = spaces.includes(space)

    fun permissionIn(space: SpaceId): Permission? =
        when (val scope = spaces) {
            SpaceScope.AllSpaces -> permission
            is SpaceScope.Listed -> scope.grants[space]?.let { minOf(it, permission) }
        }

    fun allows(
        space: SpaceId,
        needed: Permission,
    ): Boolean = permissionIn(space)?.covers(needed) == true
}
