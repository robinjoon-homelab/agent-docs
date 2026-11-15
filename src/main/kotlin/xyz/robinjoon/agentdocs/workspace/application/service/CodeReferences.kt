package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.CodeReferenceRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.history.CodeReference
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import java.util.Collections

internal object CodeReferences {
    fun resolve(
        requests: List<CodeReferenceRequest>,
        links: List<ExternalLink>,
    ): List<CodeReference> =
        Collections.unmodifiableList(
            requests
                .map { request ->
                    val repo = request.repository?.let(ExternalLink::repositoryOf) ?: inferred(request, links)
                    CodeReference(repo, request.commit?.lowercase(), request.pullRequest)
                }.distinct(),
        )

    private fun inferred(
        request: CodeReferenceRequest,
        links: List<ExternalLink>,
    ): String {
        if (request.repository != null) throw InvalidRequestException("repository must be a GitHub owner/repo")
        val repositories = links.filter { it.kind == ExternalLinkKind.GITHUB_REPOSITORY }.map { it.value }.distinct()
        if (repositories.size != 1) {
            throw InvalidRequestException("pass repository explicitly when the space has zero or multiple repositories")
        }
        return repositories.single()
    }
}
