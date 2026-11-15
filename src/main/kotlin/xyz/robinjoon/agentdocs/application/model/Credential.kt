package xyz.robinjoon.agentdocs.application.model

// Retained only for this live request; never serialize or log the raw value.
class Credential(
    val kind: Kind,
    private val secret: String,
) {
    enum class Kind { BEARER, WEB_SESSION }

    fun value(): String = secret

    override fun toString(): String = "Credential([redacted])"
}
