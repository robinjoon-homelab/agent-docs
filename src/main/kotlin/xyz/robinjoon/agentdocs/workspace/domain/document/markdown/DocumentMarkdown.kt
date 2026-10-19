package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// The whole-document form agents read and write: front matter with the header and revision, then the blocks. The
// revision in the front matter is the base a later full replace merges against.
object DocumentMarkdown {
    fun write(
        document: Document,
        space: SpaceSlug,
        annotated: Boolean,
    ): String {
        val header = document.header
        val entries =
            listOfNotNull(
                "id" to document.id.toString(),
                "space" to space.value,
                "path" to header.path.value,
                "title" to FrontMatter.quote(header.title),
                "status" to
                    header.properties.status.name
                        .lowercase(),
                "tags" to FrontMatter.list(header.properties.tags),
                header.properties.assignee?.let { "assignee" to it },
                "revision" to document.revision.toString(),
            )
        val body = BlockMarkdown.write(document.blocks, annotated)
        return FrontMatter(entries).render() + "\n\n" + body + "\n"
    }

    fun statusOf(value: String): DocumentStatus? =
        DocumentStatus.entries.firstOrNull {
            it.name.equals(value.replace('-', '_'), ignoreCase = true)
        }
}
