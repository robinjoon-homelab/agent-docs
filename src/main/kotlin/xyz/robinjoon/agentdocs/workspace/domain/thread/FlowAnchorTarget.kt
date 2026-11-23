package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen

object FlowAnchorTarget {
    fun of(
        anchor: Anchor,
        earlier: List<Block>,
        current: List<Block>,
    ): String {
        if (anchor !is Anchor.Whole) return "ok"
        val id = anchor.blockId
        if (earlier.firstOrNull() is FlowDesignBlock || current.firstOrNull() is FlowDesignBlock) {
            return DesignAnchorTarget.of(anchor, earlier, current)
        }
        val old = earlier.firstOrNull { it.id == id } ?: return "ok"
        val previousScreen = screen(earlier, id) ?: return "ok"
        val currentScreen = current.filterIsInstance<FlowScreen>().firstOrNull { it.id == previousScreen.id } ?: return "screenDeleted"
        val now = if (old is FlowElement) elements(current, currentScreen.id)[id] ?: return "elementDeleted" else currentScreen
        val unchanged = sameTarget(earlier, current, old, now)
        return if (unchanged) "ok" else "changed"
    }

    private fun sameTarget(
        earlier: List<Block>,
        current: List<Block>,
        old: Block,
        now: Block,
    ): Boolean = if (old is FlowScreen && now is FlowScreen) sameScreen(earlier, current, old, now) else comparable(old) == comparable(now)

    private fun sameScreen(
        earlier: List<Block>,
        current: List<Block>,
        old: FlowScreen,
        now: FlowScreen,
    ): Boolean =
        old.name == now.name && old.entry == now.entry && old.desc == now.desc && old.frame == now.frame &&
            elements(earlier, old.id) == elements(current, now.id)

    private fun elements(
        blocks: List<Block>,
        screen: BlockId,
    ): Map<BlockId, Block> =
        blocks.drop(blocks.indexOfFirst { it.id == screen } + 1).takeWhile { it is FlowElement }.associate {
            it.id to
                comparable(it)
        }

    private fun comparable(block: Block): Block =
        if (block is FlowElement) {
            block.copy(
                behavior = block.behavior.withoutPlacementControls(),
            )
        } else {
            block
        }

    private fun screen(
        blocks: List<Block>,
        id: BlockId,
    ): FlowScreen? = blocks.take(blocks.indexOfFirst { it.id == id } + 1).filterIsInstance<FlowScreen>().lastOrNull()
}
