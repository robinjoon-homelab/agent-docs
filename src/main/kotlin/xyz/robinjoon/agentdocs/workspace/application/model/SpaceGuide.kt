package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// The agent guide a space names, as readers get it. A guide is offered only while its document is in the space and
// outside the trash. Otherwise it is withheld with the reason, so the owner can fix the setting, and without the
// document's title or id, because a document moved to another space must not show through this one.
sealed interface SpaceGuide {
    data class Offered(
        val documentId: DocumentId,
        val title: String,
        val path: String,
    ) : SpaceGuide

    data class Withheld(
        val issue: SpaceGuideIssue,
    ) : SpaceGuide
}
