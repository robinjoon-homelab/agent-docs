ALTER TABLE document_revisions ADD COLUMN code_references TEXT;
ALTER TABLE document_revisions ADD COLUMN import_provenance TEXT;

CREATE TABLE revision_names (
    document_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    "name" VARCHAR(100) NOT NULL,
    CONSTRAINT pk_revision_names PRIMARY KEY (document_id, revision),
    CONSTRAINT fk_revision_names_revision FOREIGN KEY (document_id, revision)
        REFERENCES document_revisions (document_id, revision)
);
