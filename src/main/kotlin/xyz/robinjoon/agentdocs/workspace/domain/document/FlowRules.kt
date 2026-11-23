package xyz.robinjoon.agentdocs.workspace.domain.document

object FlowRules {
    fun isFlow(block: Block): Boolean = block is FlowSettings || block is FlowScreen || block is FlowElement || block is FlowDesignBlock

    fun validate(
        type: String,
        blocks: List<Block>,
    ) {
        if (type == "doc") {
            if (blocks.any(::isFlow)) throw InvalidDocumentException("ordinary documents cannot contain flow blocks")
            return
        }
        if (type == "flow" && blocks.any { it is FlowDesignBlock }) {
            FlowDesignRules.validate(blocks)
            return
        }
        if (type != "flow") throw InvalidDocumentException("unknown document type $type")
        validateLegacy(blocks)
    }

    private fun validateLegacy(blocks: List<Block>) {
        if (blocks.firstOrNull() !is FlowSettings || blocks.count { it is FlowSettings } != 1 ||
            blocks.any { !isFlow(it) } || blocks.getOrNull(1) !is FlowScreen
        ) {
            throw InvalidDocumentException("a flow needs settings first, then one or more screens and their elements")
        }
        blocks.zipWithNext().forEach { (previous, next) ->
            if (previous is FlowScreen && previous.frame.isNote && next is FlowElement) {
                throw InvalidDocumentException("a note page cannot contain elements")
            }
        }
        val ids = blocks.filterIsInstance<FlowScreen>().map { it.screenId }
        if (ids.distinct().size != ids.size) throw InvalidDocumentException("screen ids must be unique")
    }

    fun normalize(
        blocks: List<Block>,
        previous: List<Block> = emptyList(),
    ): List<Block> {
        if (blocks.firstOrNull() is FlowDesignBlock) return FlowDesignRules.preserveIdentity(blocks, previous)
        if (previous.firstOrNull() is FlowDesignBlock) throw InvalidDocumentException("a v4 document must retain its design model")
        val settings = blocks.firstOrNull() as? FlowSettings ?: return blocks
        val highest = blocks.filterIsInstance<FlowScreen>().maxOfOrNull { it.screenId.substringAfter('-').toBigInteger() }
        val next = (highest ?: java.math.BigInteger.ZERO) + java.math.BigInteger.ONE
        if (next > Int.MAX_VALUE.toBigInteger()) throw InvalidDocumentException("screen number is too large")
        val old = (previous.firstOrNull() as? FlowSettings)?.nextScreen ?: 1
        return listOf(settings.copy(nextScreen = maxOf(old, settings.nextScreen, next.toInt()))) + blocks.drop(1)
    }

    fun initial(
        frame: String,
        prefix: String,
    ): List<Block> =
        listOf(
            FlowSettings(BlockId.next(), frame, prefix),
            FlowScreen(BlockId.next(), "${prefix.ifEmpty { "S" }}-01"),
        )
}
