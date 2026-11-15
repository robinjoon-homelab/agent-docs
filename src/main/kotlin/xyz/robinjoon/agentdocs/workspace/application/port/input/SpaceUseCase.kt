package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.SpaceChange
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceDraft
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceGuide
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceView
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

interface SpaceUseCase {
    // Only the spaces the caller may see, most recently active first, each with its agent guide. Archived spaces only
    // when asked for.
    fun list(
        access: Access,
        includeArchived: Boolean,
    ): List<SpaceOverview>

    // A former slug finds the space too; the result carries the current slug.
    fun get(
        access: Access,
        slug: SpaceSlug,
    ): Space

    // The same space as get, with its agent guide.
    fun view(
        access: Access,
        slug: SpaceSlug,
    ): SpaceView

    // Owner only. Fails with SlugTakenException when the slug is in use or kept for an old address.
    fun create(
        context: WriteContext,
        draft: SpaceDraft,
    ): Space

    // Owner only. A new slug keeps the old one as a redirect. An agent guide must be a document of this space outside
    // the trash, and a default repository one of its linked GitHub repositories; anything else fails without a change.
    fun change(
        context: WriteContext,
        slug: SpaceSlug,
        change: SpaceChange,
    ): SpaceView

    // The one visible space linked to the repository or directory. A repository link wins over an organization link,
    // and the deepest directory link wins. Fails with SpaceNotResolvedException listing the candidates otherwise.
    fun resolve(
        access: Access,
        locator: SpaceLocator,
    ): SpaceView

    // The guide to offer next to a document of the space: null when the caller does not see the space, the space names
    // no guide, or the guide is trashed or gone.
    fun agentGuide(
        access: Access,
        space: SpaceId,
    ): SpaceGuide.Offered?
}
