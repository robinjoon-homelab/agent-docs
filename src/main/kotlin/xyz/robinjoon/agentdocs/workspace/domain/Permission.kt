package xyz.robinjoon.agentdocs.workspace.domain

// Ordered from the narrowest grant. A connection's scope is one of READ, COMMENT, or EDIT; only the instance owner
// holds OWNER, which also covers space settings, connection approval, and permanent deletion.
enum class Permission {
    READ,
    COMMENT,
    EDIT,
    OWNER,
    ;

    fun covers(needed: Permission): Boolean = this >= needed
}
