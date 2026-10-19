-- Owned by the access context. GitHub tokens are never stored; connection tokens and authorization codes are stored
-- only as SHA-256 hashes. Column names avoid SQL keywords such as scope, state, and resource.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    github_id BIGINT NOT NULL,
    login VARCHAR(39) NOT NULL,
    display_name VARCHAR(255),
    avatar_url VARCHAR(500),
    last_signed_in_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_github_id UNIQUE (github_id)
);

-- redirect_uris holds one URI per line.
CREATE TABLE oauth_clients (
    client_id VARCHAR(500) PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    client_name VARCHAR(100) NOT NULL,
    redirect_uris TEXT NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_oauth_clients_kind CHECK (kind IN ('METADATA_DOCUMENT', 'REGISTERED'))
);

-- space ids refer to workspace spaces by value; the contexts share the database but not tables.
CREATE TABLE connections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    kind VARCHAR(10) NOT NULL,
    client_id VARCHAR(500),
    client_name VARCHAR(100) NOT NULL,
    client_host VARCHAR(255),
    grant_scope VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    access_hash VARCHAR(64) NOT NULL,
    access_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    refresh_hash VARCHAR(64),
    CONSTRAINT fk_connections_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_connections_access UNIQUE (access_hash),
    CONSTRAINT uk_connections_refresh UNIQUE (refresh_hash),
    CONSTRAINT ck_connections_kind CHECK (kind IN ('AGENT', 'SCRIPT')),
    CONSTRAINT ck_connections_scope CHECK (grant_scope IN ('READ', 'COMMENT', 'EDIT'))
);

CREATE TABLE connection_spaces (
    connection_id UUID NOT NULL,
    space_id UUID NOT NULL,
    CONSTRAINT pk_connection_spaces PRIMARY KEY (connection_id, space_id),
    CONSTRAINT fk_connection_spaces_connection FOREIGN KEY (connection_id) REFERENCES connections (id)
);

-- One authorization attempt. The approval columns are set once the owner approves; code_hash is the one-time code.
CREATE TABLE authorization_requests (
    id UUID PRIMARY KEY,
    client_id VARCHAR(500) NOT NULL,
    redirect_uri VARCHAR(2000) NOT NULL,
    client_state VARCHAR(500),
    code_challenge VARCHAR(43) NOT NULL,
    resource_uri VARCHAR(500) NOT NULL,
    requested_scope VARCHAR(10),
    decision VARCHAR(10) NOT NULL,
    approver_id UUID,
    granted_scope VARCHAR(10),
    granted_spaces TEXT,
    lifetime_seconds BIGINT,
    code_hash VARCHAR(64),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_authorization_requests_code UNIQUE (code_hash),
    CONSTRAINT ck_authorization_requests_decision CHECK (decision IN ('PENDING', 'APPROVED', 'DENIED', 'EXCHANGED'))
);
