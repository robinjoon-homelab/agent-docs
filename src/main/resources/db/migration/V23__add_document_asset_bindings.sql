-- referenced_asset_id is a logical token from immutable content, not a requirement to export the original space.
CREATE TABLE document_asset_bindings (
    document_id UUID NOT NULL,
    referenced_asset_id UUID NOT NULL,
    stored_asset_id UUID NOT NULL,
    CONSTRAINT pk_document_asset_bindings PRIMARY KEY (document_id, referenced_asset_id),
    CONSTRAINT fk_document_asset_bindings_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_asset_bindings_stored FOREIGN KEY (stored_asset_id) REFERENCES assets (id)
);
CREATE INDEX ix_document_asset_bindings_stored ON document_asset_bindings (stored_asset_id);
