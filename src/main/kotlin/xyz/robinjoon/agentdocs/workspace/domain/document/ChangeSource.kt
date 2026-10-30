package xyz.robinjoon.agentdocs.workspace.domain.document

// The path a change came through, shown in history next to the actor.
enum class ChangeSource {
    WEB,
    MCP,
    REST,

    // A change the application makes on its own, such as turning folders into documents when it starts.
    SYSTEM,
}
