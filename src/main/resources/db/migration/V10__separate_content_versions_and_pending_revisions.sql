ALTER TABLE documents ADD COLUMN content_version INTEGER NOT NULL DEFAULT 1;
UPDATE documents SET content_version = revision;
ALTER TABLE document_revisions ADD COLUMN content_version INTEGER NOT NULL DEFAULT 1;
UPDATE document_revisions SET content_version = revision;
ALTER TABLE document_revisions ADD COLUMN pending BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_pending_revision_deadlines ON document_revisions (pending, updated_at, created_at);
