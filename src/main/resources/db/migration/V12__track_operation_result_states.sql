ALTER TABLE write_operations ADD COLUMN result_state VARCHAR(16) DEFAULT 'LEGACY' NOT NULL;
UPDATE write_operations SET result_state = 'AVAILABLE' WHERE payload IS NOT NULL;
ALTER TABLE write_operations ADD CONSTRAINT ck_write_operations_result_state
    CHECK (result_state IN ('LEGACY', 'AVAILABLE', 'REMOVED', 'COMPLETED'));
