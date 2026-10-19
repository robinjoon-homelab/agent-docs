package xyz.robinjoon.agentdocs.access.domain.user

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException

// The GitHub user id never changes; the login can. A person is recognized by the id and shown by the login.
data class GitHubAccount(
    val githubId: Long,
    val login: String,
) {
    init {
        if (githubId <= 0 || !loginPattern.matches(login)) {
            throw InvalidAccessException("a GitHub account has a positive id and a login of letters, digits, and hyphens")
        }
    }

    private companion object {
        val loginPattern = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")
    }
}
