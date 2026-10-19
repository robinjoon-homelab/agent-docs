package xyz.robinjoon.agentdocs.access.application.model

// The user GitHub reported after login. The GitHub token that fetched it is not part of it and is not kept.
data class GitHubIdentity(
    val githubId: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
)
