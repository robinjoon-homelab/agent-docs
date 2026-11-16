package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.domain.Permission

// What a document stream tells the browser after each check: the document changed, the caller's permission in its
// space changed, or the caller can no longer watch it. Signals carry no document content, title, or space name, and a
// permission change alone never touches the document's version.
internal object DocumentEventSignals {
    data class Signal(
        val name: String,
        val data: String,
    )

    // seen is null on the first check: the page has just read the document with its permission, so only a later
    // change is signalled. The permission comes first, so the page stops saving before it reloads the document.
    fun after(
        previous: DocumentEventSnapshot?,
        seen: Permission?,
        current: DocumentEventSnapshot,
        permission: Permission?,
    ): List<Signal> =
        listOfNotNull(
            permission?.takeIf { seen != null && it != seen }?.let { Signal("permission", """{"permission":"${it.name.lowercase()}"}""") },
            Signal("invalidate", "{}").takeIf { current != previous },
        )

    // The last signal before the stream ends because the caller lost the document or the sign-in, or null for any other
    // failure. not_found does not say whether access was revoked, the document moved to a space the caller cannot
    // see, or it was deleted, just as a request would not.
    fun ended(error: RuntimeException): Signal? =
        when (error) {
            is DocumentNotFoundException -> revoked("not_found")
            is UnauthenticatedException -> revoked("unauthenticated")
            else -> null
        }

    private fun revoked(reason: String): Signal = Signal("revoked", """{"reason":"$reason"}""")
}
