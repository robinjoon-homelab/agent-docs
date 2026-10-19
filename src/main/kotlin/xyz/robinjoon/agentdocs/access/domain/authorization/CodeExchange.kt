package xyz.robinjoon.agentdocs.access.domain.authorization

import xyz.robinjoon.agentdocs.access.domain.TokenHash

// What a token request repeats to redeem a code.
data class CodeExchange(
    val clientId: String,
    val codeHash: TokenHash,
    val redirectUri: String,
    val verifier: String,
)
