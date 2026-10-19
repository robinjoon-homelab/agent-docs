package xyz.robinjoon.agentdocs.access.application.model

// A token response. expiresIn is in seconds; scope is the granted scope name.
class TokenSet(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val scope: String,
)
