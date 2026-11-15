package xyz.robinjoon.agentdocs.workspace.domain.space

import java.util.UUID

// What a space points agents and readers to: the document holding its writing rules, terms, and templates, and the
// linked GitHub repository that short references such as #123, a commit, or a file path stand for when the space links
// several repositories. The guide is kept as a document id only. A document can be trashed or moved to another space
// without touching its space, so whether the guide is still offered is decided when it is read.
@ConsistentCopyVisibility
data class SpaceGuidance private constructor(
    val guideDocumentId: UUID?,
    val defaultRepository: String?,
) {
    fun withGuide(documentId: UUID?): SpaceGuidance = copy(guideDocumentId = documentId)

    // A repository the space no longer links stops being the default, so short references never point at a
    // repository the owner removed.
    fun within(links: List<ExternalLink>): SpaceGuidance =
        if (defaultRepository == null || defaultRepository in repositoriesOf(links)) this else copy(defaultRepository = null)

    // Only an explicitly linked repository can be chosen. An organization link alone names no repository, so no
    // repository is guessed from it.
    fun choosing(
        repository: String?,
        links: List<ExternalLink>,
    ): SpaceGuidance {
        if (repository.isNullOrBlank()) return copy(defaultRepository = null)
        val normalized =
            ExternalLink.repositoryOf(repository) ?: throw InvalidSpaceException("a default repository must look like owner/repo")
        if (normalized !in repositoriesOf(links)) {
            throw InvalidSpaceException("the default repository must be one of the GitHub repositories linked to the space")
        }
        return copy(defaultRepository = normalized)
    }

    companion object {
        val none = SpaceGuidance(null, null)

        fun of(
            guideDocumentId: UUID?,
            defaultRepository: String?,
        ): SpaceGuidance = SpaceGuidance(guideDocumentId, defaultRepository?.let(ExternalLink::repositoryOf))

        fun repositoriesOf(links: List<ExternalLink>): List<String> =
            links.filter { it.kind == ExternalLinkKind.GITHUB_REPOSITORY }.map { it.value }
    }
}
