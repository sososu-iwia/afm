ALTER TABLE users ADD COLUMN email VARCHAR(320);
CREATE UNIQUE INDEX uq_users_email_lower
    ON users(lower(email))
    WHERE email IS NOT NULL;

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    channel VARCHAR(16) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    subject VARCHAR(255),
    body VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT chk_notification_channel CHECK (channel IN ('SMS', 'EMAIL')),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notification_attempts CHECK (attempt_count >= 0 AND max_attempts > 0)
);

CREATE INDEX idx_notification_outbox_delivery
    ON notification_outbox(status, attempt_count, created_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    previous_value JSONB NOT NULL DEFAULT 'null'::jsonb,
    new_value JSONB NOT NULL DEFAULT 'null'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL
);

CREATE INDEX idx_audit_logs_entity
    ON audit_logs(entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_actor
    ON audit_logs(actor, occurred_at DESC);
CREATE INDEX idx_audit_logs_correlation
    ON audit_logs(correlation_id);
