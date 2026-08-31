CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED')),
    CONSTRAINT uq_idempotency_scope UNIQUE (actor_id, endpoint, idempotency_key)
);

CREATE INDEX idx_idempotency_records_expires_at
    ON idempotency_records(expires_at);

ALTER TABLE notification_outbox
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE notification_outbox
    DROP CONSTRAINT chk_notification_status;

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD'));

ALTER TABLE ai_processing_jobs
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
