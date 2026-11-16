-- Owned by the access context. space_id refers to a workspace space by value, as connection_spaces does; the contexts
-- share the database but not tables. A membership names the invited GitHub login until that account first signs in,
-- then keeps the user id, so a renamed login keeps it. Revoking keeps the row and clears the live keys: live_login holds
-- the lowercase login while the invitation waits, live_member the user id while it is joined, so each space has at
-- most one live membership per login and per person. NULLs never collide on H2 or PostgreSQL.
CREATE TABLE space_memberships (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL,
    invitee_login VARCHAR(39) NOT NULL,
    member_id UUID,
    member_role VARCHAR(10) NOT NULL,
    invited_by UUID NOT NULL,
    invited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    live_login VARCHAR(39),
    live_member UUID,
    CONSTRAINT fk_space_memberships_member FOREIGN KEY (member_id) REFERENCES users (id),
    CONSTRAINT fk_space_memberships_inviter FOREIGN KEY (invited_by) REFERENCES users (id),
    CONSTRAINT uk_space_memberships_live_login UNIQUE (space_id, live_login),
    CONSTRAINT uk_space_memberships_live_member UNIQUE (space_id, live_member),
    CONSTRAINT ck_space_memberships_role CHECK (member_role IN ('EDITOR', 'COMMENTER')),
    CONSTRAINT ck_space_memberships_joined CHECK (
        (member_id IS NULL AND joined_at IS NULL) OR (member_id IS NOT NULL AND joined_at IS NOT NULL)
    )
);

CREATE INDEX ix_space_memberships_live_login ON space_memberships (live_login);
CREATE INDEX ix_space_memberships_live_member ON space_memberships (live_member);
CREATE INDEX ix_space_memberships_space ON space_memberships (space_id, invited_at);
