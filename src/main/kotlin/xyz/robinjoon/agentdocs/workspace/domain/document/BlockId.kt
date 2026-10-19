package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.UUID

// The stable identity of a block, a table row, or a table column. Saving, rewriting through markdown, moving, and
// inserting rows keep it, so revision diffs, comment anchors, and agent edits all point at the same id.
@JvmInline
value class BlockId(
    val value: String,
) {
    init {
        if (!pattern.matches(value)) {
            throw InvalidDocumentException("block id must be 4 to 16 lowercase letters or digits")
        }
    }

    override fun toString(): String = value

    companion object {
        private val pattern = Regex("[a-z0-9]{4,16}")
        private const val GENERATED_LENGTH = 10
        private const val RADIX = 36

        // 10 base-36 characters taken from a random UUID: about 51 random bits, ample for the blocks of one document.
        fun next(): BlockId =
            BlockId(
                UUID
                    .randomUUID()
                    .mostSignificantBits
                    .toULong()
                    .toString(RADIX)
                    .padStart(GENERATED_LENGTH, '0')
                    .takeLast(GENERATED_LENGTH),
            )
    }
}
