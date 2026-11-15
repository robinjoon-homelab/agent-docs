package xyz.robinjoon.agentdocs.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind

internal object ActivitiesTable : Table("activities") {
    val id = long("id").autoIncrement()
    val kind = varchar("kind", 60)
    val actorKind = enumerationByName("actor_kind", 10, ActivityActorKind::class)
    val actorUserId = javaUUID("actor_user_id").nullable()
    val actorLogin = varchar("actor_login", 39).nullable()
    val actorConnectionId = javaUUID("actor_connection_id").nullable()
    val actorClientName = varchar("actor_client_name", 200).nullable()
    val spaceId = javaUUID("space_id").nullable()
    val documentId = javaUUID("document_id").nullable()
    val threadId = javaUUID("thread_id").nullable()
    val revision = integer("revision").nullable()
    val summary = varchar("summary", 300)
    val occurredAt = timestampWithTimeZone("occurred_at")
    val createdCommentId = javaUUID("created_comment_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

// The logins an entry's created comment mentioned. Rows go with their entry when the log is pruned.
internal object ActivityMentionsTable : Table("activity_mentions") {
    val activityId = long("activity_id")
    val login = varchar("login", 39)

    override val primaryKey = PrimaryKey(activityId, login)
}
