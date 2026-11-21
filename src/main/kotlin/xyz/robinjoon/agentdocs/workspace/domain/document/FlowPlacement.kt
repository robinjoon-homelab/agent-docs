package xyz.robinjoon.agentdocs.workspace.domain.document

data class FlowPlacement(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    init {
        if (x !in 0..10000 || y !in 0..10000 || w !in 16..10000 || h !in 16..10000) {
            throw InvalidDocumentException("invalid flow placement")
        }
    }
}
