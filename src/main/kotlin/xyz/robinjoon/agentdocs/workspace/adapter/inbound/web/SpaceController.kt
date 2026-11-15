package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.GuidanceChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.SettingUpdate
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceChange
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceDraft
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.net.URI
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/spaces")
class SpaceController(
    private val spaces: SpaceUseCase,
) {
    @GetMapping
    fun list(principal: Principal?): List<Map<String, Any?>> =
        spaces.list(CallerAccess.of(principal), includeArchived = true).map(SpaceJson::overview)

    @PostMapping
    fun create(
        @RequestBody request: SpaceRequest,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val profile = SpaceProfile.of(request.name, request.description.orEmpty(), linksOf(request.links.orEmpty()))
        val space = spaces.create(CallerAccess.write(principal, null, null), SpaceDraft(SpaceSlug(request.slug.trim()), profile))
        return ResponseEntity.created(URI.create("/api/spaces/${space.slug}")).body(SpaceJson.space(space, null))
    }

    // A former slug answers with the space under its current slug, so the web app can replace its address.
    @GetMapping("/{slug}")
    fun get(
        @PathVariable slug: String,
        principal: Principal?,
    ): Map<String, Any?> = SpaceJson.view(spaces.view(CallerAccess.of(principal), SpaceSlug(slug)))

    // Read field by field, because "agentGuideDocumentId": null and "defaultRepository": null clear those settings
    // while leaving them out keeps them.
    @PatchMapping("/{slug}")
    fun change(
        @PathVariable slug: String,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val change =
            SpaceChange(
                Wire.optional(body, "slug")?.trim()?.let(::SpaceSlug),
                Wire.optional(body, "name"),
                Wire.optional(body, "description"),
                body
                    .get("links")
                    ?.takeUnless(JsonNode::isNull)
                    ?.values()
                    ?.map(::linkRequestOf)
                    ?.let(::linksOf),
                body.get("archived")?.takeIf(JsonNode::isBoolean)?.booleanValue(),
                guidanceOf(body),
            )
        return SpaceJson.view(spaces.change(CallerAccess.write(principal, null, null), SpaceSlug(slug), change))
    }

    @PostMapping("/resolve")
    fun resolve(
        @RequestBody request: ResolveRequest,
        principal: Principal?,
    ): Map<String, Any?> = SpaceJson.view(spaces.resolve(CallerAccess.of(principal), SpaceLocator(request.remote, request.path)))

    private fun guidanceOf(body: JsonNode): GuidanceChange? {
        val guide = settingOf(body, GUIDE) { DocumentId(uuidOf(it)) }
        val repository = settingOf(body, REPOSITORY) { it }
        return if (guide == null && repository == null) null else GuidanceChange(guide, repository)
    }

    // A missing field keeps the setting; null or an empty string clears it.
    private fun <T> settingOf(
        body: JsonNode,
        field: String,
        parse: (String) -> T,
    ): SettingUpdate<T>? {
        val node = body.get(field) ?: return null
        if (!node.isNull && !node.isString) throw InvalidRequestException("$field must be a string or null")
        val value = Wire.optional(body, field)?.trim().orEmpty()
        return if (value.isEmpty()) SettingUpdate.Clear else SettingUpdate.To(parse(value))
    }

    private fun uuidOf(value: String): UUID =
        try {
            UUID.fromString(value)
        } catch (exception: IllegalArgumentException) {
            throw InvalidRequestException("$GUIDE must be a document id", "pick a document listed in this space", exception)
        }

    private fun linkRequestOf(node: JsonNode): LinkRequest = LinkRequest(Wire.required(node, "kind"), Wire.required(node, "value"))

    private fun linksOf(links: List<LinkRequest>): List<ExternalLink> =
        links.map { link ->
            val kind =
                ExternalLinkKind.entries.firstOrNull { it.name.equals(link.kind, ignoreCase = true) }
                    ?: throw InvalidRequestException("link kind must be github_repository, github_organization, local_path, or url")
            ExternalLink.of(kind, link.value)
        }

    private companion object {
        const val GUIDE = "agentGuideDocumentId"
        const val REPOSITORY = "defaultRepository"
    }
}

data class SpaceRequest(
    val slug: String,
    val name: String,
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
