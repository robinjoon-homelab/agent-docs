package xyz.robinjoon.agentdocs.workspace.adapter.outbound.render

import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderInput
import java.util.UUID

internal class RenderPage(
    private val json: ObjectMapper,
    private val script: String,
    private val css: String,
) {
    fun html(input: DesignRenderInput): String {
        val payload =
            mapOf(
                "blocks" to input.blocks.map { mapOf("type" to "flowDesign", "id" to it.id.value, "design" to it.fields) },
                "screen" to input.screen,
                "state" to input.state,
                "target" to input.selection.target?.fields(),
                "mode" to input.selection.mode,
                "assets" to input.assets,
            )
        val nonce = UUID.randomUUID().toString()
        // Escape all markup delimiters in stored JSON before putting it in the inert script data block.
        val data =
            json
                .writeValueAsString(payload)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026")
        val policy =
            "default-src 'none'; script-src 'nonce-$nonce'; style-src 'unsafe-inline'; img-src data:; " +
                "font-src data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-src 'none'"
        return """<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="$policy">""" +
            "<style>$css</style></head><body><div id=\"root\"></div><script type=\"application/json\" id=\"design-input\">$data</script>" +
            "<script nonce=\"$nonce\" type=\"module\">$script</script></body></html>"
    }
}
