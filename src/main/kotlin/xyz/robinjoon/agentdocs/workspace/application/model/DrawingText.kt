package xyz.robinjoon.agentdocs.workspace.application.model

// One text element of a drawing as the person typed it, with the shape it sits in when it is a label.
data class DrawingText(
    val elementId: String,
    val text: String,
    val containerId: String?,
)
