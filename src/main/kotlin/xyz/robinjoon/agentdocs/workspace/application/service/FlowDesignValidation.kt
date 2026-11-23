package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignValidator
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowWarning
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

internal fun FlowDesignValidator.validateContent(
    id: DocumentId,
    blocks: List<Block>,
    previous: List<Block> = emptyList(),
): List<FlowWarning> {
    if (blocks.none { it is FlowDesignBlock }) return emptyList()
    val design = blocks.filterIsInstance<FlowDesignBlock>()
    if (design.size != blocks.size) throw InvalidDocumentException("legacy and v4 design blocks cannot be mixed")
    val old = previous.filterIsInstance<FlowDesignBlock>()
    val warnings =
        validate(FlowDesignBlock.documentJson(id, design), old.takeIf { it.isNotEmpty() }?.let { FlowDesignBlock.documentJson(id, it) })
    val ids = design.associate { it.designId to it.id }
    return warnings.map { FlowWarning(ids.getValue(it.designId), it.code, it.detail) }
}
