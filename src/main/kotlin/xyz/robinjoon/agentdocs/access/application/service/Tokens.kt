package xyz.robinjoon.agentdocs.access.application.service

import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.domain.TokenHash

// Raw tokens carry a prefix that tells a person or a secret scanner what they are: adt_a_ access, adt_r_ refresh,
// adt_s_ script, adt_c_ authorization code.
internal object Tokens {
    const val ACCESS = "adt_a_"
    const val REFRESH = "adt_r_"
    const val SCRIPT = "adt_s_"
    const val CODE = "adt_c_"

    fun next(
        secrets: SecretSource,
        prefix: String,
    ): Pair<String, TokenHash> {
        val raw = prefix + secrets.next()
        return raw to TokenHash.of(raw)
    }
}
