package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// The content version stays stable when pending input is confirmed without changing the body.
object DocumentMarkdown {
    fun write(
        document: Document,
        space: SpaceSlug,
        annotated: Boolean,
    ): String = parts(document, space, annotated, document.blocks).joinToString("")

    internal fun parts(
        document: Document,
        space: SpaceSlug,
        annotated: Boolean,
        blocks: List<Block>,
    ): Sequence<String> {
        val header = document.header
        val entries =
            listOfNotNull(
                "id" to document.id.toString(),
                "space" to space.value,
                "path" to header.path.value,
                "title" to FrontMatter.quote(header.title),
                ("type" to header.type).takeIf { header.type == "flow" },
                (document.blocks.firstOrNull() as? xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings)?.let {
                    "frame" to
                        it.frame
                },
                (document.blocks.firstOrNull() as? xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings)?.let {
                    "idPrefix" to
                        FrontMatter.quote(it.idPrefix)
                },
                "status" to
                    header.properties.status.name
                        .lowercase(),
                "tags" to FrontMatter.list(header.properties.tags),
                header.properties.assignee?.let { "assignee" to it },
                "version" to document.version.toString(),
            )
        val head = FrontMatter(entries).render() + "\n\n"
        return sequenceOf(head) + BlockMarkdown.parts(blocks, annotated) + sequenceOf("\n")
    }

    fun statusOf(value: String): DocumentStatus? =
        DocumentStatus.entries.firstOrNull {
            it.name.equals(value.replace('-', '_'), ignoreCase = true)
        }
}
