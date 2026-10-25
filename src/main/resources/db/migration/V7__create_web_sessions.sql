-- One browser's sign-in. token_hash is the SHA-256 of the cookie token; a sign-in unused for 30 days is deleted.
CREATE TABLE web_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_web_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_web_sessions_token UNIQUE (token_hash)
);

CREATE INDEX ix_web_sessions_last_used ON web_sessions (last_used_at);
