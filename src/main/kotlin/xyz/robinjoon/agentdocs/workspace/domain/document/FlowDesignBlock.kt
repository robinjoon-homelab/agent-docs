package xyz.robinjoon.agentdocs.workspace.domain.document

// The outer ID belongs to revisions/comments; references inside a design use its independent model ID.
class FlowDesignBlock private constructor(
    override val id: BlockId,
    val fields: Map<String, Any?>,
    val json: String,
) : Block {
    val kind: String = required("kind")
    val designId: String = required("id")

    fun string(field: String): String? = fields[field] as? String

    fun withFields(values: Map<String, Any?>): FlowDesignBlock = parse(id, DesignJsonWriter.write(values))

    override fun withId(id: BlockId): FlowDesignBlock = FlowDesignBlock(id, fields, json)

    override fun plainText(): String = listOfNotNull(string("name"), string("entry"), string("description")).joinToString(" ")

    override fun equals(other: Any?): Boolean = other is FlowDesignBlock && id == other.id && json == other.json

    override fun hashCode(): Int = 31 * id.hashCode() + json.hashCode()

    private fun required(key: String): String =
        string(key)?.takeIf { it.isNotEmpty() } ?: throw InvalidDocumentException("flow-design needs a nonempty $key")

    companion object {
        fun parse(
            id: BlockId,
            json: String,
        ): FlowDesignBlock {
            val fields = DesignJsonReader(json).read()
            return FlowDesignBlock(id, fields, DesignJsonWriter.write(fields))
        }

        fun documentJson(
            documentId: DocumentId,
            blocks: List<FlowDesignBlock>,
        ): String = blocks.joinToString(",", "{\"id\":\"$documentId\",\"blocks\":[", "]}") { it.json }
    }
}
