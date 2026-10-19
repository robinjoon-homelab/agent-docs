package xyz.robinjoon.agentdocs.workspace.application.model

enum class MarkdownFormat {
    // Front matter and a "<!-- b:id h:hash -->" annotation before each block, for agents that edit.
    ANNOTATED,

    // The same markdown without annotations, for reading and export.
    PLAIN,

    // Headings and one line per block with its id, type, and size, for finding a section in a large document.
    OUTLINE,
}
