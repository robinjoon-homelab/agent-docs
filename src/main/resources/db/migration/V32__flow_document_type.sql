ALTER TABLE documents ADD COLUMN document_type VARCHAR(4) NOT NULL DEFAULT 'doc';
ALTER TABLE document_revisions ADD COLUMN document_type VARCHAR(4) NOT NULL DEFAULT 'doc';
ALTER TABLE documents ADD CONSTRAINT documents_type_check CHECK (document_type IN ('doc', 'flow'));
ALTER TABLE document_revisions ADD CONSTRAINT revisions_type_check CHECK (document_type IN ('doc', 'flow'));
