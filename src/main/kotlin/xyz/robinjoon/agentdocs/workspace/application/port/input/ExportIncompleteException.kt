package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.ExportAsset

class ExportIncompleteException(
    val failedAsset: ExportAsset,
    cause: Throwable,
) : RuntimeException("export could not include all attachment originals", cause) {
    val fix: String =
        "ask the operator to restore the missing original from a verified backup, then retry; retain attachment metadata and document history"
}
