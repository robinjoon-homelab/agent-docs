package xyz.robinjoon.agentdocs.workspace.application.model

// What an agent knows about where it works: a git remote, a local directory, or both.
data class SpaceLocator(
    val remote: String?,
    val path: String?,
) {
    init {
        if (remote.isNullOrBlank() && path.isNullOrBlank()) {
            throw InvalidRequestException(
                "pass a git remote or a local path",
                "run `git remote get-url origin` and pass the result as remote",
            )
        }
    }
}
