package xyz.robinjoon.agentdocs.access.domain.user

enum class UserRole {
    // The one account named at installation: sees every space and manages connections.
    OWNER,

    // Allowed to sign in, by the members setting or a live space invitation, and sees only the spaces it joined.
    MEMBER,
}
