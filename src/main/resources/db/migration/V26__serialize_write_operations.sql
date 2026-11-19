-- These rows serialize attempts for the same (actor, opId), even before the result exists.
CREATE TABLE write_operation_locks (
    bucket INT PRIMARY KEY
);

INSERT INTO write_operation_locks (bucket)
SELECT high.digit * 16 + low.digit
FROM (VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9), (10), (11), (12), (13), (14), (15)) high(digit)
CROSS JOIN (VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9), (10), (11), (12), (13), (14), (15)) low(digit);

-- A removed result keeps the document's last space for current permission checks after it is purged.
ALTER TABLE write_operations ADD COLUMN purged_from_space_id UUID;

UPDATE write_operations
SET purged_from_space_id = (
    SELECT space_id
    FROM activities
    WHERE kind = 'document.purged' AND document_id = write_operations.target_id
    ORDER BY id DESC
    LIMIT 1
)
WHERE result_state = 'REMOVED' AND (kind LIKE 'document.%' OR kind = 'revision.name');
