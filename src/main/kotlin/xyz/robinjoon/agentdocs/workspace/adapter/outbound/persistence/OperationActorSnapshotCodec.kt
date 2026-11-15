package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import java.util.UUID

internal object OperationActorSnapshotCodec {
    fun encode(actor: Actor): Map<String, Any?> =
        mapOf(
            "kind" to actor.kind.name,
            "user" to actor.userId.toString(),
            "login" to actor.login,
            "connection" to actor.agent?.connectionId?.toString(),
            "client" to actor.agent?.clientName,
        )

    fun decode(node: JsonNode): Actor =
        Actor(
            ActorKind.valueOf(node.text("kind")),
            node.uuid("user"),
            node.text("login"),
            node.optionalText("connection")?.let {
                AgentIdentity(UUID.fromString(it), node.text("client"))
            },
        )
}
