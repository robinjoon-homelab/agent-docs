package xyz.robinjoon.agentdocs.access.application.service

// The redirect back to the client. iss is always included so the client can tell which server answered (RFC 9207).
// Values are percent-encoded here as UTF-8, leaving only RFC 3986 unreserved characters as they are.
internal object OAuthRedirects {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val HEX = "0123456789ABCDEF"
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0F
    private const val BYTE_MASK = 0xFF

    fun build(
        redirectUri: String,
        parameters: List<Pair<String, String?>>,
    ): String {
        val query =
            parameters
                .mapNotNull { (name, value) -> value?.let { name + "=" + encode(it) } }
                .joinToString("&")
        val separator = if ('?' in redirectUri) "&" else "?"
        return redirectUri + separator + query
    }

    fun encode(value: String): String =
        value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val code = byte.toInt() and BYTE_MASK
            if (code < UNRESERVED_LIMIT &&
                code.toChar() in UNRESERVED
            ) {
                code.toChar().toString()
            } else {
                "%" + HEX[code shr NIBBLE_BITS] + HEX[code and NIBBLE_MASK]
            }
        }

    private const val UNRESERVED_LIMIT = 0x80
}
