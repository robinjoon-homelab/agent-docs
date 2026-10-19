package xyz.robinjoon.agentdocs.access.application.model

// An OAuth error with its standard code (RFC 6749 section 5.2, RFC 7591 section 3.2.2). status is the HTTP status the
// token and registration endpoints answer with.
class OAuthException(
    val error: String,
    description: String,
    val status: Int = BAD_REQUEST,
    cause: Throwable? = null,
) : RuntimeException(description, cause) {
    companion object {
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
    }
}
