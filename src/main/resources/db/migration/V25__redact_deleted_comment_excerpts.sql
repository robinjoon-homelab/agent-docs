-- V21 added explicit comment IDs to creation activities. Earlier activities have only the thread ID.
-- Keep the recovered link separate so it does not turn historical log rows into webhook events.
ALTER TABLE activities ADD COLUMN legacy_excerpt_comment_id UUID;

-- Both comment position and activity ID increase within a thread. Match from the end because retention can
-- remove the oldest activities. Check kind, actor and time as well before assigning an old excerpt.
UPDATE activities
SET legacy_excerpt_comment_id = (
    SELECT comments.id
    FROM (
        SELECT c.*, ROW_NUMBER() OVER (PARTITION BY thread_id ORDER BY comment_position DESC) AS recent_position,
            LEAD(created_at) OVER (PARTITION BY thread_id ORDER BY comment_position) AS next_comment_at
        FROM thread_comments c
    ) comments
    JOIN (
        SELECT id, thread_id, ROW_NUMBER() OVER (PARTITION BY thread_id ORDER BY id DESC) AS recent_position
        FROM activities
        WHERE kind IN ('thread.opened', 'comment.added') OR created_comment_id IS NOT NULL
    ) creations ON creations.thread_id = comments.thread_id AND creations.recent_position = comments.recent_position
    WHERE creations.id = activities.id
        AND (activities.kind = 'thread.opened') = (comments.comment_position = 0)
        AND activities.actor_kind = comments.author_kind
        AND activities.actor_user_id = comments.author_user_id
        AND activities.actor_connection_id IS NOT DISTINCT FROM comments.author_connection_id
        AND activities.occurred_at >= comments.created_at
        AND (comments.next_comment_at IS NULL OR activities.occurred_at <= comments.next_comment_at)
)
WHERE created_comment_id IS NULL AND kind IN ('thread.opened', 'comment.added');

CREATE INDEX ix_activities_legacy_excerpt_comment ON activities (legacy_excerpt_comment_id);
CREATE INDEX ix_inbox_items_comment ON inbox_items (comment_id);

UPDATE inbox_items
SET excerpt = ''
WHERE comment_id IN (SELECT id FROM thread_comments WHERE deleted_at IS NOT NULL);

-- Historical summaries embed the excerpt in free text. The actor, subject, timestamps and mention rows stay intact.
UPDATE activities
SET summary = '삭제된 댓글'
WHERE kind IN ('thread.opened', 'comment.added')
    AND COALESCE(created_comment_id, legacy_excerpt_comment_id) IN (
        SELECT id FROM thread_comments WHERE deleted_at IS NOT NULL
    );
