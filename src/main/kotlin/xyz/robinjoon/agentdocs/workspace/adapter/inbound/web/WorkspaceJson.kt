package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.workspace.application.model.ActivityView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedWrite
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.space.Space

// Response shapes shared by the workspace endpoints. Enum values are written in lowercase.
object WorkspaceJson {
    fun writeResult(
        written: DocumentWrite,
        all: Boolean = false,
    ): Map<String, Any?> {
        val changed = written.changedBlocks.toSet()
        val blocks =
            written.document.blocks.filter { all || it.id in changed }.map {
                mapOf(
                    "id" to it.id.value,
                    "hash" to BlockJson.write(it)["hash"],
                )
            }
        return mapOf(
            "documentId" to written.document.id.value,
            "type" to written.document.header.type,
            "warnings" to WorkspaceJson.warnings(written),
            "revision" to written.revision,
            "version" to written.document.version,
            "unchanged" to written.unchanged,
            "changedBlockIds" to written.changedBlocks.map { it.value },
            "blocks" to blocks,
            "alsoChanged" to written.related.map(WorkspaceJson::related),
        )
    }

    fun warnings(write: xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite): List<Map<String, String>> {
        val warnings =
            write.designWarnings +
                xyz.robinjoon.agentdocs.workspace.domain.document.FlowWarnings
                    .of(write.document.blocks)
        return warnings.map { warning ->
            val fields = mapOf("blockId" to warning.blockId.value, "code" to warning.code)
            warning.detail?.let { fields + ("detail" to it) } ?: fields
        }
    }

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

    // The caller's current permission in a space it sees, which the web app uses to decide what it offers there: read,
    // comment, edit, or owner. MCP answers leave it out.
    fun permission(
        access: Access,
        space: SpaceId,
    ): Map<String, Any?> = mapOf("permission" to access.permissionIn(space)?.name?.lowercase())

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
            "type" to header.type,
            "status" to
                header.properties.status.name
                    .lowercase(),
            "tags" to header.properties.tags,
            "assignee" to header.properties.assignee,
            "revision" to lifecycle.revision,
            "version" to lifecycle.version,
            "createdAt" to lifecycle.createdAt,
            "updatedAt" to lifecycle.updatedAt,
            "trashedAt" to lifecycle.trashedAt,
        )

    fun related(related: RelatedWrite): Map<String, Any?> =
        mapOf(
            "documentId" to related.document.id.value,
            "path" to related.document.header.path.value,
            "title" to related.document.header.title,
            "revision" to related.document.revision,
            "version" to related.document.version,
            "change" to related.change.name.lowercase(),
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
            "membershipId" to record.subject.membershipId,
            "connectionId" to record.subject.connectionId,
            "summary" to record.summary,
            "at" to record.at,
        )
    }
}
