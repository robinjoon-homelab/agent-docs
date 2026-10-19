package xyz.robinjoon.agentdocs.access.domain.user

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException

// Who may sign in, from the instance settings. Logins compare without case, as GitHub does.
class Allowlist(
    owner: String,
    members: Set<String>,
) {
    private val owner = owner.trim().lowercase()
    private val members = members.map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()

    init {
        if (this.owner.isEmpty()) {
            throw InvalidAccessException("the owner login must be set")
        }
    }

    // null means the account may not sign in at all.
    fun roleOf(login: String): UserRole? =
        when (login.lowercase()) {
            owner -> UserRole.OWNER
            in members -> UserRole.MEMBER
            else -> null
        }
}
