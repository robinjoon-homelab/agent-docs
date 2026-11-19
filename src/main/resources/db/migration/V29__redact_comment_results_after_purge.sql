-- A successful author-delete operation identifies the exact comment even after its tombstone was purged.
-- A thread-only deletion activity does not identify which comment was deleted.
UPDATE activities
SET summary = '삭제된 댓글'
WHERE kind IN ('thread.opened', 'comment.added')
    AND COALESCE(created_comment_id, legacy_excerpt_comment_id) IN (
        SELECT target_id FROM write_operations WHERE kind = 'comment.delete'
    );

UPDATE inbox_items
SET excerpt = ''
WHERE comment_id IN (SELECT target_id FROM write_operations WHERE kind = 'comment.delete');
