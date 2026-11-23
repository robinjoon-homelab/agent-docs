package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonReader
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal class DesignSelectionLink(
    val screen: String,
    val state: String?,
    val target: DesignAddress?,
) {
    companion object {
        fun from(document: String?): DesignSelectionLink? {
            if (document == null || '?' !in document) return null
            val params =
                URI.create(document).rawQuery.orEmpty().split('&').associate { param ->
                    param.substringBefore('=') to URLDecoder.decode(param.substringAfter('=', ""), StandardCharsets.UTF_8)
                }
            val screen = params["flowScreen"] ?: return null
            return DesignSelectionLink(screen, params["flowState"], target(params))
        }

        private fun target(params: Map<String, String>): DesignAddress? {
            val node = params["flowElement"] ?: return null
            val owner = params["flowOwner"] ?: params.getValue("flowScreen")
            val via = params["flowVia"]?.let { DesignJsonReader("""{"via":$it}""").read()["via"] } ?: emptyList<Any>()
            return DesignAddress.from(mapOf("owner" to owner, "node" to node, "via" to via))
        }
    }
}
