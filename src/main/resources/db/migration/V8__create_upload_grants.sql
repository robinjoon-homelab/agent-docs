-- One file an agent may send from its shell. Rows live for minutes and are removed once expired; only the secret's
-- hash is stored.
CREATE TABLE upload_grants (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL,
    issuer_kind VARCHAR(10) NOT NULL,
    issuer_user_id UUID NOT NULL,
    issuer_login VARCHAR(39) NOT NULL,
    issuer_connection_id UUID,
    issuer_client_name VARCHAR(200),
    alt TEXT NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_upload_grants_space FOREIGN KEY (space_id) REFERENCES spaces (id),
    CONSTRAINT uk_upload_grants_secret UNIQUE (secret_hash)
);

CREATE INDEX ix_upload_grants_expires ON upload_grants (expires_at);
