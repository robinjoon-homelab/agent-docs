package xyz.robinjoon.agentdocs.workspace.domain.document.design

import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

typealias DesignFields = Map<*, *>

data class DesignAddress(
    val owner: String,
    val node: String,
    val via: List<Map<String, String>> = emptyList(),
) {
    fun fields(): Map<String, Any> = mapOf("owner" to owner, "node" to node) + if (via.isEmpty()) emptyMap() else mapOf("via" to via)

    fun matches(value: Any?): Boolean {
        val address = value as? DesignFields ?: return false
        return address["owner"] == owner && address["node"] == node && (address["via"] ?: emptyList<Any>()) == via
    }

    companion object {
        fun from(fields: DesignFields): DesignAddress {
            val owner = fields["owner"] as? String ?: invalidDesign("target.owner is required")
            val node = fields["node"] as? String ?: invalidDesign("target.node is required")
            val via = fields["via"] ?: emptyList<Any>()
            if (fields.keys.any { it !in setOf("owner", "node", "via") } || via !is List<*>) invalidDesign("invalid design address")
            return DesignAddress(owner, node, via.map(::hop))
        }

        private fun hop(value: Any?): Map<String, String> {
            val fields = value as? DesignFields ?: invalidDesign("invalid address hop")
            val keys = fields.keys
            if (keys != setOf("owner", "instance", "component") && keys != setOf("owner", "repeat", "itemKey")) {
                invalidDesign("address hop needs owner + instance/component or repeat/itemKey")
            }
            return fields.entries.associate { it.key.toString() to (it.value as? String ?: invalidDesign("invalid hop ID")) }
        }
    }
}

internal fun invalidDesign(message: String): Nothing = throw InvalidDocumentException(message)

internal fun Any?.objects(): List<DesignFields> = (this as? List<*>)?.filterIsInstance<DesignFields>().orEmpty()

internal fun Any?.stringFields(): Map<String, Any?> =
    (this as? DesignFields)?.entries?.associate { it.key.toString() to it.value }.orEmpty()
