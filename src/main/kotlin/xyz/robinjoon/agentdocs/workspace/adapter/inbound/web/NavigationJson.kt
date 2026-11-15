package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.application.model.NavigationPage

object NavigationJson {
    fun page(page: NavigationPage): Map<String, Any> =
        mapOf(
            "documents" to
                page.documents.map { entry ->
                    mapOf(
                        "id" to entry.id.value,
                        "spaceId" to entry.spaceId.value,
                        "spaceSlug" to entry.spaceSlug,
                        "spaceName" to entry.spaceName,
                        "path" to entry.path,
                        "title" to entry.title,
                    )
                },
            "total" to page.total,
        )
}
