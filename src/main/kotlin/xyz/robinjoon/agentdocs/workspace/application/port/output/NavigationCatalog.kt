package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.NavigationDocument
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

interface NavigationCatalog {
    // Current live documents, including archived spaces. No pagination before access filtering.
    fun documents(spaces: Set<SpaceId>): List<NavigationDocument>

    fun linkingTo(
        target: DocumentId,
        spaces: Set<SpaceId>,
    ): List<NavigationDocument>
}
