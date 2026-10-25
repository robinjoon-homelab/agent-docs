package xyz.robinjoon.agentdocs.access.application.model

// The person behind a browser's sign-in. renewed means the sign-in was just extended, so the browser should get its
// cookie again with a fresh lifetime.
data class SessionCaller(
    val caller: Caller,
    val renewed: Boolean,
)
