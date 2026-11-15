package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.GuideCandidate
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// Light reads of the documents spaces name as their agent guide. Ids without a document are left out.
interface SpaceGuideCatalog {
    fun find(ids: Set<DocumentId>): List<GuideCandidate>
}
