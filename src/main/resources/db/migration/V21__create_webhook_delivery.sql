-- The shared activity log learns which entry created a comment and whom that comment mentioned, so the webhook
-- context can send comment and mention events from the log alone. Existing entries keep NULL and no mentions.
ALTER TABLE activities ADD COLUMN created_comment_id UUID;

CREATE TABLE activity_mentions (
    activity_id BIGINT NOT NULL,
    login VARCHAR(39) NOT NULL,
    CONSTRAINT pk_activity_mentions PRIMARY KEY (activity_id, login),
    CONSTRAINT fk_activity_mentions_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE CASCADE
);

-- Webhook passes reread the log from an endpoint's start by these two filters.
CREATE INDEX ix_activities_kind ON activities (kind, id);
CREATE INDEX ix_activities_created_comment ON activities (created_comment_id);

-- Owned by the webhook context. The signing secret is kept as written because every attempt signs again; it is shown
-- only when created or replaced. Endpoints are stopped, never deleted.
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY,
    url VARCHAR(2000) NOT NULL,
    event_types VARCHAR(100) NOT NULL,
    signing_secret VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    secret_rotated_at TIMESTAMP WITH TIME ZONE,
    stopped_at TIMESTAMP WITH TIME ZONE,
    stop_reason VARCHAR(10),
    CONSTRAINT ck_webhook_endpoints_stop CHECK (
        (stopped_at IS NULL AND stop_reason IS NULL) OR (stopped_at IS NOT NULL AND stop_reason IN ('OWNER', 'GONE'))
    )
);

-- One event for one endpoint, kept after it finishes. source_activity_id refers to the shared log by value: the log is
-- pruned after a year while delivery records stay, and the payload holds everything a retry sends. mentioned_login is
-- empty for events about no single mention, so the unique key holds without NULL comparisons. Column names avoid SQL
-- keywords such as state.
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    source_activity_id BIGINT NOT NULL,
    mentioned_login VARCHAR(39) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload TEXT NOT NULL,
    delivery_state VARCHAR(10) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_status INT,
    last_error VARCHAR(40),
    CONSTRAINT fk_webhook_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints (id),
    CONSTRAINT uk_webhook_deliveries_event UNIQUE (endpoint_id, source_activity_id, event_type, mentioned_login),
    CONSTRAINT ck_webhook_deliveries_type CHECK (event_type IN ('DOCUMENT_CHANGED', 'COMMENT_CREATED', 'COMMENT_MENTIONED')),
    CONSTRAINT ck_webhook_deliveries_state CHECK (delivery_state IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_webhook_deliveries_next CHECK ((delivery_state = 'PENDING') = (next_attempt_at IS NOT NULL)),
    CONSTRAINT ck_webhook_deliveries_attempts CHECK (attempts BETWEEN 0 AND 8)
);

CREATE INDEX ix_webhook_deliveries_due ON webhook_deliveries (delivery_state, next_attempt_at);
