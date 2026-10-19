package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.SpaceChange
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceDraft
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

interface SpaceUseCase {
    // Only the spaces the caller may see, most recently active first.
    fun list(access: Access): List<SpaceOverview>

    // A former slug finds the space too; the result carries the current slug.
    fun get(
        access: Access,
        slug: SpaceSlug,
    ): Space

    // Owner only. Fails with SlugTakenException when the slug is in use or kept for an old address.
    fun create(
        context: WriteContext,
        draft: SpaceDraft,
    ): Space

    // Owner only. A new slug keeps the old one as a redirect.
    fun change(
        context: WriteContext,
        slug: SpaceSlug,
        change: SpaceChange,
    ): Space

    // The one visible space linked to the repository or directory. A repository link wins over an organization link,
    // and the deepest directory link wins. Fails with SpaceNotResolvedException listing the candidates otherwise.
    fun resolve(
        access: Access,
        locator: SpaceLocator,
    ): Space
}
