package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.workspace.application.model.ActivityView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.space.Space

// Response shapes shared by the workspace endpoints. Enum values are written in lowercase.
object WorkspaceJson {
    fun actor(actor: Actor): Map<String, Any?> =
        mapOf(
            "kind" to actor.kind.name.lowercase(),
            "login" to actor.login,
            "agent" to actor.agent?.let { mapOf("connectionId" to it.connectionId, "clientName" to it.clientName) },
        )

    fun activityActor(actor: ActivityActor): Map<String, Any?> =
        mapOf(
            "kind" to actor.kind.name.lowercase(),
            "login" to actor.login,
            "agent" to
                actor.connectionId?.takeIf { actor.kind == ActivityActorKind.AGENT }?.let {
                    mapOf(
                        "connectionId" to it,
                        "clientName" to actor.clientName,
                    )
                },
        )

    fun spaceRef(space: Space): Map<String, Any> = mapOf("id" to space.id.value, "slug" to space.slug.value, "name" to space.profile.name)

    fun space(space: Space): Map<String, Any?> =
        spaceRef(space) +
            mapOf(
                "description" to space.profile.description,
                "links" to space.profile.links.map { mapOf("kind" to it.kind.name.lowercase(), "value" to it.value) },
                "formerSlugs" to space.address.formerSlugs.map { it.value },
                "createdAt" to space.createdAt,
                "archivedAt" to space.lifecycle.archivedAt,
            )

    fun overview(overview: SpaceOverview): Map<String, Any?> =
        space(overview.space) + mapOf("documentCount" to overview.documentCount, "lastActivityAt" to overview.lastActivityAt)

    fun header(
        header: DocumentHeader,
        lifecycle: DocumentLifecycle,
    ): Map<String, Any?> =
        mapOf(
            "path" to header.path.value,
            "title" to header.title,
            "status" to
                header.properties.status.name
                    .lowercase(),
            "tags" to header.properties.tags,
            "assignee" to header.properties.assignee,
            "revision" to lifecycle.revision,
            "createdAt" to lifecycle.createdAt,
            "updatedAt" to lifecycle.updatedAt,
            "trashedAt" to lifecycle.trashedAt,
        )

    fun listing(listing: DocumentListing): Map<String, Any?> =
        mapOf("id" to listing.id.value) + header(listing.header, listing.lifecycle) + mapOf("updatedBy" to actor(listing.updatedBy))

    fun document(
        document: Document,
        space: Space,
    ): Map<String, Any?> =
        mapOf("id" to document.id.value, "space" to spaceRef(space)) + header(document.header, document.lifecycle) +
            mapOf("blocks" to document.blocks.map(BlockJson::write))

    fun view(view: DocumentView): Map<String, Any?> =
        document(view.document, view.space) +
            mapOf(
                "blockChanges" to
                    view.marks.map {
                        mapOf(
                            "blockId" to it.blockId.value,
                            "actor" to actor(it.actor),
                            "revision" to it.revision,
                            "at" to it.at,
                        )
                    },
            )

    fun activity(view: ActivityView): Map<String, Any?> {
        val record = view.entry.record
        return mapOf(
            "id" to view.entry.id,
            "kind" to record.kind,
            "actor" to activityActor(record.actor),
            "space" to view.space?.let(::spaceRef),
            "documentId" to record.subject.documentId,
            "documentTitle" to view.documentTitle,
            "threadId" to record.subject.threadId,
            "revision" to record.subject.revision,
            "summary" to record.summary,
            "at" to record.at,
        )
    }
}
