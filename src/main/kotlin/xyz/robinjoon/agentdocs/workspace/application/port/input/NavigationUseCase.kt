package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.MobileMemoTarget
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationPage
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

interface NavigationUseCase {
    fun isFavorite(
        access: Access,
        id: DocumentId,
    ): Boolean

    fun mobileTarget(
        access: Access,
        space: String,
        path: String,
    ): MobileMemoTarget?

    fun recent(
        access: Access,
        limit: Int,
    ): NavigationPage

    fun favorites(
        access: Access,
        limit: Int,
    ): NavigationPage

    fun recordView(
        context: WriteContext,
        id: DocumentId,
    )

    fun favorite(
        context: WriteContext,
        id: DocumentId,
        enabled: Boolean,
    )

    fun backlinks(
        access: Access,
        id: DocumentId,
        limit: Int,
    ): NavigationPage
}
