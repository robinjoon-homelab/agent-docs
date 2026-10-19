package xyz.robinjoon.agentdocs.access.application.model

import java.io.Serializable
import java.util.UUID

// Who is making a request, as other contexts receive it. A person signed in through GitHub has no agent; the owner
// sees every space (spaces null). An agent carries its connection and sees exactly the spaces it was granted. It is
// kept in the HTTP session, so it is serializable.
data class Caller(
    val userId: UUID,
    val login: String,
    val owner: Boolean,
    val agent: CallerAgent?,
    val spaces: Set<UUID>?,
    val scope: CallerScope,
) : Serializable
