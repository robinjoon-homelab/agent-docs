CREATE TABLE edit_session_scopes (
    scope VARCHAR(150) PRIMARY KEY
);
CREATE TABLE edit_sessions (
    id UUID PRIMARY KEY,
    scope VARCHAR(150) NOT NULL,
    document_id UUID NOT NULL,
    space_id UUID NOT NULL,
    actor_kind VARCHAR(10) NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_login VARCHAR(39) NOT NULL,
    actor_connection_id UUID,
    actor_client_name VARCHAR(200),
    tab_id UUID,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(16),
    superseded_by UUID,
    CHECK (last_activity_at >= started_at),
    CHECK ((ended_at IS NULL AND reason IS NULL) OR (ended_at >= started_at AND reason IS NOT NULL))
);
CREATE INDEX edit_sessions_document_idx ON edit_sessions(document_id);
CREATE INDEX edit_sessions_scope_idx ON edit_sessions(scope);
CREATE TABLE edit_session_inputs (
    document_id UUID NOT NULL,
    version INTEGER NOT NULL,
    batch_id UUID NOT NULL UNIQUE,
    PRIMARY KEY (document_id, version)
);
CREATE TABLE edit_session_operations (
    scope VARCHAR(150) NOT NULL,
    operation VARCHAR(110) NOT NULL,
    session_id UUID NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    reason VARCHAR(16),
    PRIMARY KEY (scope, operation)
);
