package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import java.util.Collections

class ParsedMarkdown(
    val frontMatter: FrontMatter,
    blocks: List<ParsedBlock>,
) {
    val blocks: List<ParsedBlock> = Collections.unmodifiableList(blocks.toList())
}
