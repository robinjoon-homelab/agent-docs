-- When the owner archived a space; an archived space drops out of lists and lookups but keeps its documents.
ALTER TABLE spaces ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;
