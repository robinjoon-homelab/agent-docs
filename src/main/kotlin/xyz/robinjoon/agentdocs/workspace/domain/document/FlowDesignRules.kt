package xyz.robinjoon.agentdocs.workspace.domain.document

import java.math.BigDecimal
import java.math.BigInteger

internal object FlowDesignRules {
    fun validate(blocks: List<Block>) {
        val designs = blocks.filterIsInstance<FlowDesignBlock>()
        if (designs.size != blocks.size) fail("legacy and v4 blocks cannot be mixed")
        if (designs.first().kind != "settings" ||
            designs.count { it.kind == "settings" } != 1
        ) {
            fail("exactly one settings block must come first")
        }
        val version = designs.first().fields["modelVersion"] as? BigDecimal
        if (version?.compareTo(4.toBigDecimal()) != 0) fail("settings must specify modelVersion 4")
        val byId = designs.associateBy { it.designId }
        if (byId.size != designs.size) fail("design block ids must be unique")
        val screens = designs.filter { it.kind == "screen" }
        if (screens.isEmpty()) fail("at least one screen is required")
        if (screens.map { it.string("code") }.distinct().size != screens.size) fail("screen codes must be unique")
        designs.filter { it.kind == "state" || it.kind == "annotation" }.forEach { owned(it, byId) }
    }

    fun preserveIdentity(
        blocks: List<Block>,
        previous: List<Block>,
    ): List<Block> {
        val previousDesigns = previous.filterIsInstance<FlowDesignBlock>()
        val byId = previousDesigns.associateBy { it.id }
        val byDesignId = previousDesigns.associateBy { it.designId }
        blocks.filterIsInstance<FlowDesignBlock>().forEach { next ->
            val old = byId[next.id]
            if (old != null && (next.designId != old.designId || next.kind != old.kind)) fail("a block must keep its design id and kind")
            if (byDesignId[next.designId]?.id?.let { it != next.id } == true) fail("a surviving design must keep its block id")
        }
        return preserveScreenNumber(blocks, previous)
    }

    private fun preserveScreenNumber(
        blocks: List<Block>,
        previous: List<Block>,
    ): List<Block> {
        val settings = blocks.first() as FlowDesignBlock
        if (settings.kind != "settings") return blocks
        val stated = (settings.fields["nextScreen"] as? BigDecimal) ?: return blocks
        if (stated < BigDecimal.ONE || stated.stripTrailingZeros().scale() > 0) fail("nextScreen must be a positive integer")
        val previousNumber = (previous.firstOrNull() as? FlowDesignBlock)?.fields?.get("nextScreen") as? BigDecimal
        val highest =
            blocks
                .filterIsInstance<FlowDesignBlock>()
                .filter { it.kind == "screen" }
                .mapNotNull { it.string("code")?.substringAfterLast('-')?.toBigIntegerOrNull() }
                .maxOrNull()
        val next = maxOf(stated, previousNumber ?: BigDecimal.ONE, (highest ?: BigInteger.ZERO).inc().toBigDecimal())
        return if (next > stated) listOf(settings.withFields(settings.fields + ("nextScreen" to next))) + blocks.drop(1) else blocks
    }

    private fun owned(
        block: FlowDesignBlock,
        byId: Map<String, FlowDesignBlock>,
    ) {
        val owner = byId[block.string("screen")]
        if (owner?.kind != "screen" ||
            (block.kind == "state" && owner.string("frame") == "note")
        ) {
            fail("invalid owning screen for ${block.designId}")
        }
        val state = block.string("state") ?: return
        val parent = byId[state]
        if (parent?.kind != "state" || parent.string("screen") != owner.designId) fail("invalid owning state for ${block.designId}")
    }

    private fun fail(message: String): Nothing = throw InvalidDocumentException("flow-design: $message")
}
