package xyz.robinjoon.agentdocs.workspace.adapter.outbound.tokenization

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import xyz.robinjoon.agentdocs.workspace.application.port.output.MarkdownTokenCounter

class O200kMarkdownTokenCounter : MarkdownTokenCounter {
    private val encoding by lazy { Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE) }

    // Markdown can contain token-like literals. Treat them as document text.
    override fun count(text: String): Int = encoding.countTokensOrdinary(text)
}
