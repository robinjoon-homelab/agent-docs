package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyFlowUpgrade
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignFields

internal object DesignAnchorTarget {
    // Use the same projection as the upgrade so an unchanged historical comment survives the format boundary.
    fun of(
        anchor: Anchor.Whole,
        earlier: List<Block>,
        current: List<Block>,
    ): String {
        val before = DesignCommentRevision(designs(earlier), anchor.viewedStateId)
        val after = DesignCommentRevision(designs(current), anchor.viewedStateId)
        val original = before.target(anchor.blockId) ?: return "ok"
        val screen = before.screen(original) ?: return "ok"
        val currentScreen = after.target(screen.id) ?: return "screenDeleted"
        if (currentScreen.kind != "screen" || currentScreen.designId != screen.designId) return "screenDeleted"
        val now = after.target(anchor.blockId) ?: return "elementDeleted"
        if (now.kind != original.kind || after.screen(now)?.id != screen.id) return "elementDeleted"
        if (original.kind == "annotation" && after.lostDrawing(now, before, original)) return "elementDeleted"
        return if (before.signature(original) == after.signature(now)) "ok" else "changed"
    }

    private fun designs(blocks: List<Block>): List<FlowDesignBlock> =
        if (blocks.firstOrNull() is FlowSettings) LegacyFlowUpgrade.convert(blocks) else blocks.filterIsInstance<FlowDesignBlock>()
}

private class DesignCommentRevision(
    private val blocks: List<FlowDesignBlock>,
    private val viewedStateId: BlockId?,
) {
    private val byId = blocks.associateBy { it.id }
    private val byKey = blocks.associateBy { it.designId }
    private val scenes = mutableMapOf<Pair<BlockId, BlockId?>, DesignCommentScene>()

    fun target(id: BlockId): FlowDesignBlock? = byId[id]

    fun screen(block: FlowDesignBlock): FlowDesignBlock? =
        when (block.kind) {
            "screen" -> block
            "state", "annotation" -> byKey[block.string("screen")]
            else -> null
        }

    private fun scene(
        screen: FlowDesignBlock,
        state: FlowDesignBlock? = null,
    ): DesignCommentScene = scenes.getOrPut(screen.id to state?.id) { DesignCommentScene(blocks, screen, state) }

    private fun address(block: FlowDesignBlock): DesignAddress? = designTargetAddress(block.fields["target"])

    fun lostDrawing(
        now: FlowDesignBlock,
        before: DesignCommentRevision,
        original: FlowDesignBlock,
    ): Boolean {
        if (before.context(original)?.id != context(now)?.id) return true
        return before.hasTarget(original) && !hasTarget(now)
    }

    private fun context(block: FlowDesignBlock): FlowDesignBlock? {
        val own = block.string("state")
        val state = if (viewedStateId == null) byKey[own] else byId[viewedStateId]
        if (state?.kind != "state" || state.string("screen") != block.string("screen")) return null
        return state.takeIf { own == null || own == it.designId }
    }

    private fun hasTarget(block: FlowDesignBlock): Boolean {
        val parent = screen(block) ?: return false
        val target = address(block) ?: return false
        return scene(parent, context(block)).contains(target)
    }

    fun signature(block: FlowDesignBlock): Any =
        when (block.kind) {
            "annotation" -> annotation(block)
            "state" -> state(block)
            "screen" -> whole(block)
            else -> block
        }

    private fun annotation(block: FlowDesignBlock): List<Any?> {
        val parent = requireNotNull(screen(block))
        val drawing = scene(parent, context(block))
        return listOf(block, address(block)?.let(drawing::snapshot))
    }

    private fun state(block: FlowDesignBlock): List<Any?> {
        val base = scene(requireNotNull(screen(block)))
        val changes = (block.fields["changes"] as? List<*>)?.filterIsInstance<DesignFields>().orEmpty()
        val touched =
            changes.flatMap { change ->
                val placement = change["at"] as? DesignFields
                listOfNotNull(change["target"], placement?.get("parent"), placement?.get("before"), placement?.get("after"))
            }
        return listOf(block, touched.mapNotNull(::designTargetAddress).distinct().associateWith(base::snapshot))
    }

    private fun whole(block: FlowDesignBlock): List<Any?> {
        val scoped = blocks.filter { it.string("screen") == block.designId }
        val states = scoped.filter { it.kind == "state" }.map { it.id }
        val annotations = scoped.filter { it.kind == "annotation" && it.string("state") == null }.associateBy { it.id }
        return listOf(block.fields - "root", states, annotations, scene(block).all())
    }
}

internal fun designTargetAddress(raw: Any?): DesignAddress? {
    val fields = raw as? DesignFields ?: return null
    if (fields["owner"] !is String || fields["node"] !is String) return null
    return DesignAddress.from(fields)
}
