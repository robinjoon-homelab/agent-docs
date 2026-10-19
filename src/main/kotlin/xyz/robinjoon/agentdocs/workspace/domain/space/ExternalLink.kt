package xyz.robinjoon.agentdocs.workspace.domain.space

// Something outside the platform that a space is about. Links only help agents find their space and turn references
// into links; they never grant or limit access. The private constructor keeps every value normalized.
@ConsistentCopyVisibility
data class ExternalLink private constructor(
    val kind: ExternalLinkKind,
    val value: String,
) {
    // An organization link matches every repository of that organization.
    fun matchesRepository(repository: String): Boolean =
        when (kind) {
            ExternalLinkKind.GITHUB_REPOSITORY -> value == repository
            ExternalLinkKind.GITHUB_ORGANIZATION -> repository.substringBefore('/') == value
            else -> false
        }

    // A path link covers the directory itself and everything below it, but not a sibling that shares a prefix.
    fun coversPath(path: String): Boolean = kind == ExternalLinkKind.LOCAL_PATH && (path == value || path.startsWith("$value/"))

    companion object {
        private const val MAX_LENGTH = 2000
        private val githubName = Regex("[a-z0-9](?:[a-z0-9._-]{0,99})")

        fun of(
            kind: ExternalLinkKind,
            raw: String,
        ): ExternalLink {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) {
                throw InvalidSpaceException("a link must be 1 to $MAX_LENGTH characters")
            }
            return ExternalLink(kind, normalize(kind, trimmed))
        }

        // Accepts owner/repo, an https URL, or an ssh remote, so a git remote can be pasted as it is.
        fun repositoryOf(remote: String): String? {
            val path =
                remote
                    .trim()
                    .removeSuffix("/")
                    .removeSuffix(".git")
                    .replace(Regex("^(?:https?://|ssh://)?(?:git@)?github\\.com[:/]"), "")
                    .lowercase()
            val parts = path.split('/')
            return if (parts.size == 2 && parts.all(githubName::matches)) path else null
        }

        private fun normalize(
            kind: ExternalLinkKind,
            value: String,
        ): String =
            when (kind) {
                ExternalLinkKind.GITHUB_REPOSITORY -> repositoryOf(value) ?: invalid("a repository must look like owner/repo")
                ExternalLinkKind.GITHUB_ORGANIZATION -> organizationOf(value)
                ExternalLinkKind.LOCAL_PATH -> pathOf(value)
                ExternalLinkKind.URL -> urlOf(value)
            }

        private fun organizationOf(value: String): String {
            val name = value.removeSuffix("/").substringAfterLast('/').lowercase()
            return if (githubName.matches(name)) name else invalid("an organization must be a GitHub name")
        }

        private fun pathOf(value: String): String {
            val path = value.trimEnd('/')
            // Agents report an absolute working directory, so a ~ path would never match one.
            return if (path.startsWith("/")) path else invalid("a local path must be absolute, starting with /")
        }

        private fun urlOf(value: String): String =
            if (value.startsWith("https://") || value.startsWith("http://")) value else invalid("a URL must start with http:// or https://")

        private fun invalid(message: String): Nothing = throw InvalidSpaceException(message)
    }
}
