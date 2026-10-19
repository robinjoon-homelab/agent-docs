package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceChange
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceDraft
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotResolvedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceStatisticsCatalog
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.SlugTakenException
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.UUID

@Transactional(readOnly = true)
class SpaceService(
    private val spaces: SpaceRepository,
    private val statistics: SpaceStatisticsCatalog,
    private val journal: WriteJournal,
) : SpaceUseCase {
    override fun list(access: Access): List<SpaceOverview> {
        val visible = spaces.findAll().filter { access.sees(it.id) }
        val stats = statistics.forSpaces(visible.map { it.id }.toSet())
        return visible
            .map { SpaceOverview(it, stats[it.id]?.documentCount ?: 0, stats[it.id]?.lastActivityAt) }
            .sortedByDescending { it.lastActivityAt ?: it.space.createdAt }
    }

    override fun get(
        access: Access,
        slug: SpaceSlug,
    ): Space = spaces.findBySlug(slug)?.takeIf { access.sees(it.id) } ?: throw SpaceNotFoundException(slug)

    // The slug check also covers former slugs, which a unique index on the current slug alone would miss.
    @Transactional
    override fun create(
        context: WriteContext,
        draft: SpaceDraft,
    ): Space {
        Permissions.requireOwner(context.access, "creating a space")
        if (spaces.findBySlug(draft.slug) != null) throw SlugTakenException(draft.slug)
        val space = spaces.add(Space.create(SpaceId(UUID.randomUUID()), draft.slug, draft.profile, journal.now()))
        journal.record(context, "space.created", subjectOf(space), "스페이스 생성: ${space.profile.name}")
        return space
    }

    @Transactional
    override fun change(
        context: WriteContext,
        slug: SpaceSlug,
        change: SpaceChange,
    ): Space {
        Permissions.requireOwner(context.access, "changing space settings")
        val space = spaces.findBySlugForUpdate(slug)?.takeIf { context.access.sees(it.id) } ?: throw SpaceNotFoundException(slug)
        change.slug?.let { requireFree(it, space) }
        val profile =
            SpaceProfile.of(
                change.name ?: space.profile.name,
                change.description ?: space.profile.description,
                change.links ?: space.profile.links,
            )
        val changed = (change.slug?.let(space::moveTo) ?: space).describe(profile)
        if (changed == space) return space
        val saved = spaces.update(changed)
        journal.record(context, "space.changed", subjectOf(saved), "스페이스 설정 변경: ${saved.profile.name}")
        return saved
    }

    override fun resolve(
        access: Access,
        locator: SpaceLocator,
    ): Space {
        val visible = spaces.findAll().filter { access.sees(it.id) }
        val repository = locator.remote?.let(ExternalLink::repositoryOf)
        val byRepository = repository?.let { repositoryMatches(visible, it) }.orEmpty()
        val candidates = byRepository.ifEmpty { locator.path?.let { pathMatches(visible, it.trimEnd('/')) }.orEmpty() }
        candidates.singleOrNull()?.let { return it }
        val target = listOfNotNull(repository ?: locator.remote, locator.path).joinToString(" or ")
        val message = if (candidates.isEmpty()) "no space you can see is linked to $target" else "several spaces are linked to $target"
        throw SpaceNotResolvedException(message, candidates.ifEmpty { visible })
    }

    private fun requireFree(
        slug: SpaceSlug,
        space: Space,
    ) {
        val holder = spaces.findBySlug(slug)
        if (holder != null && holder.id != space.id) throw SlugTakenException(slug)
    }

    // A link to the repository itself is a closer match than a link to its organization.
    private fun repositoryMatches(
        spaces: List<Space>,
        repository: String,
    ): List<Space> {
        val direct =
            spaces.filter { space ->
                space.profile.links.any {
                    it.kind == ExternalLinkKind.GITHUB_REPOSITORY &&
                        it.matchesRepository(repository)
                }
            }
        return direct.ifEmpty { spaces.filter { space -> space.profile.links.any { it.matchesRepository(repository) } } }
    }

    // The deepest linked directory containing the path wins, so a project folder beats its parent folder.
    private fun pathMatches(
        spaces: List<Space>,
        path: String,
    ): List<Space> {
        val depths =
            spaces.mapNotNull { space ->
                space.profile.links.filter { it.coversPath(path) }.maxOfOrNull { it.value.length }?.let {
                    space to
                        it
                }
            }
        val deepest = depths.maxOfOrNull { it.second } ?: return emptyList()
        return depths.filter { it.second == deepest }.map { it.first }
    }

    private fun subjectOf(space: Space): ActivitySubject = ActivitySubject(space.id.value, null, null, null)
}
