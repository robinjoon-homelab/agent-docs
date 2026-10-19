package xyz.robinjoon.agentdocs.access.domain.user

enum class UserRole {
    // The one account named at installation: sees every space and manages connections.
    OWNER,

    // Allowed to sign in but sees only what it is invited to, which is nothing until invitations exist.
    MEMBER,
}
