-- What a space points agents to. agent_guide_document_id names the document with the space's writing rules, terms,
-- and templates. It is not a foreign key: a trashed, deleted, or moved document keeps the setting, and reading it then
-- withholds the guide. default_repository is the linked GitHub repository, as owner/repo, that short references stand
-- for when the space links several; the application clears it when that link is removed.
ALTER TABLE spaces ADD COLUMN agent_guide_document_id UUID;
ALTER TABLE spaces ADD COLUMN default_repository VARCHAR(201);
