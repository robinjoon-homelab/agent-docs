package xyz.robinjoon.agentdocs.workspace.application.model

// What a checked Excalidraw scene contains, read from the original bytes without changing them. Counts and texts skip
// deleted elements, which the scene keeps for undo but nobody sees.
class VerifiedScene(
    val elementCount: Int,
    val fileCount: Int,
    val texts: List<DrawingText>,
)
