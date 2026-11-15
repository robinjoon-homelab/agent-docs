package xyz.robinjoon.agentdocs.workspace.application.model

enum class SpaceGuideIssue {
    // The document is in the space's trash.
    TRASHED,

    // The document was deleted for good or now belongs to another space.
    UNAVAILABLE,
}
