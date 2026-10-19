package xyz.robinjoon.agentdocs.workspace.domain.space

class SlugTakenException(
    slug: SpaceSlug,
    cause: Throwable? = null,
) : RuntimeException("slug is already used by a space or kept for an old address: $slug", cause)
