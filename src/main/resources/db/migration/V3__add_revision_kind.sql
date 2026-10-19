-- What made each revision: an edit, a restore, or a move into or out of the trash. Earlier revisions count as edits.
ALTER TABLE document_revisions ADD COLUMN kind VARCHAR(10) DEFAULT 'EDIT' NOT NULL;
