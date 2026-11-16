package xyz.robinjoon.agentdocs.workspace.domain

// Space grants are bounded by Access.permission, the global ceiling. Ordered from the narrowest grant.
// A connection holds READ, COMMENT, or EDIT; only the instance owner holds OWNER, which also covers
// space settings, connection approval, and permanent deletion.
enum class Permission {
    READ,
    COMMENT,
    EDIT,
    OWNER,
    ;

    fun covers(needed: Permission): Boolean = this >= needed
}
