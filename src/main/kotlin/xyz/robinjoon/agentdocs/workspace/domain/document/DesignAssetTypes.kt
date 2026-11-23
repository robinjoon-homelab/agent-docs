package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import java.util.UUID

/** An asset-valued property can flow through nested component inputs before reaching a paint or icon. */
internal class DesignAssetTypes {
    private val types = mutableMapOf<DesignAssetProperty, Set<AssetMediaType>>()
    private val forwarded = mutableSetOf<Pair<DesignAssetProperty, DesignAssetProperty>>()
    private val literals = mutableSetOf<Pair<DesignAssetProperty, AssetId>>()
    private val direct = mutableMapOf<AssetId, Set<AssetMediaType>>()

    fun consume(
        value: Any?,
        allowed: Set<AssetMediaType>,
        property: DesignAssetProperty?,
    ) {
        when {
            value is String -> direct.narrow(assetId(value), allowed)
            property != null -> types.narrow(property, allowed)
        }
    }

    fun input(
        target: DesignAssetProperty,
        value: Any?,
        source: DesignAssetProperty?,
    ) {
        when {
            value is String -> literals.add(target to assetId(value))
            source != null -> forwarded.add(target to source)
        }
    }

    fun result(): Map<AssetId, Set<AssetMediaType>> {
        propagate()
        literals.forEach { (prop, id) -> direct.narrow(id, types[prop] ?: any) }
        return direct.toMap()
    }

    private fun propagate() {
        val sources = forwarded.groupBy({ it.first }, { it.second })
        val pending = ArrayDeque(types.keys)
        while (pending.isNotEmpty()) {
            val target = pending.removeFirst()
            val allowed = types.getValue(target)
            pending.addAll(sources[target].orEmpty().filter { types.narrow(it, allowed) })
        }
    }

    private fun <K> MutableMap<K, Set<AssetMediaType>>.narrow(
        key: K,
        allowed: Set<AssetMediaType>,
    ): Boolean {
        val previous = this[key] ?: any
        val next = previous.intersect(allowed)
        if (next.isEmpty()) throw InvalidDocumentException("conflicting asset roles for $key")
        return put(key, next) != next
    }

    private fun assetId(raw: String): AssetId {
        if (!uuid.matches(raw)) throw InvalidDocumentException("design asset must be an uploaded asset UUID")
        return AssetId(UUID.fromString(raw))
    }

    companion object {
        private val uuid = Regex("[a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12}")
        val any: Set<AssetMediaType> = AssetMediaType.entries.toSet()
        val image: Set<AssetMediaType> = AssetMediaType.entries.filter(AssetMediaType::isImage).toSet()
        val svg: Set<AssetMediaType> = setOf(AssetMediaType.SVG)
        val font: Set<AssetMediaType> = setOf(AssetMediaType.BINARY)
    }
}

internal data class DesignAssetProperty(
    val owner: String,
    val path: List<String>,
)
