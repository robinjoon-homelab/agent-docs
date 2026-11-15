package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

// Values, never arbitrary URLs. Links always stay on GitHub.
data class CodeReference(
    val repository: String,
    val commit: String?,
    val pullRequest: Int?,
) {
    init {
        if (!repositoryPattern.matches(repository) || (commit == null && pullRequest == null)) {
            throw InvalidDocumentException("a code reference needs owner/repo and a commit or pull request")
        }
        if (commit != null && !Regex("[a-fA-F0-9]{40}").matches(commit)) {
            throw InvalidDocumentException("commit must be a full 40-character SHA")
        }
        if (pullRequest != null && pullRequest < 1) throw InvalidDocumentException("pullRequest must be positive")
    }

    val commitUrl: String? get() = commit?.let { "https://github.com/$repository/commit/$it" }
    val pullRequestUrl: String? get() = pullRequest?.let { "https://github.com/$repository/pull/$it" }

    companion object {
        private val repositoryPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}/[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}")
    }
}
