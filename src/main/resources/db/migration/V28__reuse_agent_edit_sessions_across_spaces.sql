-- Past transfers could leave more than one open row for a connection and document.
-- Keep the most recently active identity open, and close the others in place at their last request.
UPDATE edit_sessions
SET ended_at = last_activity_at, reason = 'ESTIMATED'
WHERE id IN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY document_id, actor_user_id, actor_connection_id
            ORDER BY last_activity_at DESC, started_at DESC, id DESC
        ) AS open_position
        FROM edit_sessions
        WHERE actor_kind = 'AGENT' AND ended_at IS NULL
    ) positions WHERE open_position > 1
);

UPDATE edit_sessions
SET scope = CONCAT('agent:', document_id, ':', actor_user_id, ':', actor_connection_id)
WHERE actor_kind = 'AGENT';
