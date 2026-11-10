package xyz.robinjoon.agentdocs.access.application.model

import java.util.UUID

// The bearer an agent's shell sends with one file: adt_u_<connection id>.<secret>. The two parts are for the two
// contexts. Access reads the connection to say who is calling and whether that connection still stands; workspace holds
// the grant behind the secret and decides whether the token is genuine. The format sits in this model because it is
// the one thing both may read.
object UploadToken {
    const val ENDPOINT = "/api/uploads"

    private const val PREFIX = "adt_u_"
    private val format = Regex("$PREFIX([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.(.+)")

    fun of(
        connection: UUID,
        secret: String,
    ): String = "$PREFIX$connection.$secret"

    fun connectionOf(token: String): UUID? = format.matchEntire(token)?.let { UUID.fromString(it.groupValues[1]) }

    fun secretOf(token: String): String? = format.matchEntire(token)?.groupValues?.get(2)
}
