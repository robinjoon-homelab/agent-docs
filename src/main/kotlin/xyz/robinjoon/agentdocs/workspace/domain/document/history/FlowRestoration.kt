package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

internal object FlowRestoration {
    fun insertion(
        current: List<Block>,
        earlier: List<Block>,
        original: Block,
    ): Int {
        val oldIndex = earlier.indexOfFirst { it.id == original.id }
        if (original is FlowSettings) return 0
        if (original is FlowScreen) {
            val following =
                earlier
                    .drop(oldIndex + 1)
                    .filterIsInstance<FlowScreen>()
                    .map { it.id }
                    .toSet()
            return current.indexOfFirst { it.id in following }.takeIf { it >= 0 } ?: current.size
        }
        val screen =
            earlier.take(oldIndex).filterIsInstance<FlowScreen>().lastOrNull()
                ?: throw InvalidDocumentException("element has no original screen")
        val screenAt = current.indexOfFirst { it.id == screen.id }
        if (screenAt < 0) throw InvalidDocumentException("restore the element's screen first")
        return afterPreviousElement(current, earlier, original.id, screen.id)
    }

    private fun afterPreviousElement(
        current: List<Block>,
        earlier: List<Block>,
        id: BlockId,
        screen: BlockId,
    ): Int {
        val before =
            earlier
                .drop(earlier.indexOfFirst { it.id == screen } + 1)
                .takeWhile { it.id != id }
                .map { it.id }
                .toSet()
        val currentScreen = current.indexOfFirst { it.id == screen }
        val elements = current.drop(currentScreen + 1).takeWhile { it is FlowElement }
        val previous = elements.indexOfLast { it.id in before }
        return currentScreen + previous + 2
    }
}
