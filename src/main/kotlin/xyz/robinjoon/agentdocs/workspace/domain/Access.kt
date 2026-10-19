package xyz.robinjoon.agentdocs.workspace.domain

// What one request may do. Use cases receive it with every call and answer a space outside the scope exactly like a
// missing one, so neither a person nor an agent can learn that it exists.
data class Access(
    val actor: Actor,
    val spaces: SpaceScope,
    val permission: Permission,
) {
    val isOwner: Boolean get() = permission == Permission.OWNER

    fun sees(space: SpaceId): Boolean = spaces.includes(space)

    fun allows(
        space: SpaceId,
        needed: Permission,
    ): Boolean = sees(space) && permission.covers(needed)
}
