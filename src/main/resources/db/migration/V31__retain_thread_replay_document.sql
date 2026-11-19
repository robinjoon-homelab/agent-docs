-- Keep the immutable document identity after a deleted comment's snapshot has been discarded.
-- Its space may still change between deleting that comment and purging the whole document.
ALTER TABLE write_operations ADD COLUMN thread_document_id UUID;

UPDATE write_operations
SET thread_document_id = COALESCE(
    (SELECT document_id FROM threads WHERE id = write_operations.target_id),
    (SELECT document_id FROM activities
     WHERE thread_id = write_operations.target_id AND document_id IS NOT NULL
     ORDER BY id DESC LIMIT 1)
)
WHERE kind IN ('thread.open', 'thread.change', 'thread.react', 'thread.claim', 'thread.suggest', 'thread.suggestion.decide')
  AND result_state IN ('AVAILABLE', 'REMOVED', 'LEGACY');

-- thread.reply records the new comment's ID, as do later edits and deletions of that comment.
UPDATE write_operations
SET thread_document_id = COALESCE(
    (SELECT t.document_id FROM thread_comments c JOIN threads t ON t.id = c.thread_id
     WHERE c.id = write_operations.target_id),
    (SELECT document_id FROM activities
     WHERE COALESCE(created_comment_id, legacy_excerpt_comment_id) = write_operations.target_id AND document_id IS NOT NULL
     ORDER BY id DESC LIMIT 1)
)
WHERE kind IN ('thread.reply', 'comment.edit', 'comment.delete')
  AND result_state IN ('AVAILABLE', 'REMOVED', 'LEGACY');

-- For a past purge, trust its recorded final space, never the space of an earlier comment.
UPDATE write_operations
SET purged_from_space_id = (
    SELECT space_id FROM activities
    WHERE kind = 'document.purged' AND document_id = write_operations.thread_document_id
    ORDER BY id DESC LIMIT 1
)
WHERE result_state IN ('REMOVED', 'LEGACY') AND thread_document_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM documents WHERE id = write_operations.thread_document_id);
