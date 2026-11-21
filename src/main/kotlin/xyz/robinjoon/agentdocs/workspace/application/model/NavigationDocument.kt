package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.time.Instant

data class NavigationDocument
    // quality-exception: Navigation response carries document type alongside the existing location fields.
    @Suppress("LongParameterList")
    constructor(
        val id: DocumentId,
        val spaceId: SpaceId,
        val spaceSlug: String,
        val spaceName: String,
        val path: String,
        val title: String,
        val type: String = "doc",
    )

data class PersonalDocument(
    val documentId: DocumentId,
    val viewedAt: Instant?,
    val favoriteAt: Instant?,
)

data class NavigationPage(
    val documents: List<NavigationDocument>,
    val total: Int,
)
