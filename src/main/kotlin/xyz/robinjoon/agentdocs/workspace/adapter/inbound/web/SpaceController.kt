package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceChange
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceDraft
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.net.URI
import java.security.Principal

@RestController
@RequestMapping("/api/spaces")
class SpaceController(
    private val spaces: SpaceUseCase,
) {
    @GetMapping
    fun list(principal: Principal?): List<Map<String, Any?>> = spaces.list(CallerAccess.of(principal)).map(WorkspaceJson::overview)

    @PostMapping
    fun create(
        @RequestBody request: SpaceRequest,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val profile = SpaceProfile.of(request.name, request.description.orEmpty(), linksOf(request.links.orEmpty()))
        val space = spaces.create(CallerAccess.write(principal, null, null), SpaceDraft(SpaceSlug(request.slug.trim()), profile))
        return ResponseEntity.created(URI.create("/api/spaces/${space.slug}")).body(WorkspaceJson.space(space))
    }

    // A former slug answers with the space under its current slug, so the web app can replace its address.
    @GetMapping("/{slug}")
    fun get(
        @PathVariable slug: String,
        principal: Principal?,
    ): Map<String, Any?> = WorkspaceJson.space(spaces.get(CallerAccess.of(principal), SpaceSlug(slug)))

    @PatchMapping("/{slug}")
    fun change(
        @PathVariable slug: String,
        @RequestBody request: SpaceChangeRequest,
        principal: Principal?,
    ): Map<String, Any?> {
        val change = SpaceChange(request.slug?.trim()?.let(::SpaceSlug), request.name, request.description, request.links?.let(::linksOf))
        return WorkspaceJson.space(spaces.change(CallerAccess.write(principal, null, null), SpaceSlug(slug), change))
    }

    @PostMapping("/resolve")
    fun resolve(
        @RequestBody request: ResolveRequest,
        principal: Principal?,
    ): Map<String, Any?> = WorkspaceJson.space(spaces.resolve(CallerAccess.of(principal), SpaceLocator(request.remote, request.path)))

    private fun linksOf(links: List<LinkRequest>): List<ExternalLink> =
        links.map { link ->
            val kind =
                ExternalLinkKind.entries.firstOrNull { it.name.equals(link.kind, ignoreCase = true) }
                    ?: throw InvalidRequestException("link kind must be github_repository, github_organization, local_path, or url")
            ExternalLink.of(kind, link.value)
        }
}

data class SpaceRequest(
    val slug: String,
    val name: String,
    val description: String?,
    val links: List<LinkRequest>?,
)

data class SpaceChangeRequest(
    val slug: String?,
    val name: String?,
    val description: String?,
    val links: List<LinkRequest>?,
)

data class LinkRequest(
    val kind: String,
    val value: String,
)

data class ResolveRequest(
    val remote: String?,
    val path: String?,
)
