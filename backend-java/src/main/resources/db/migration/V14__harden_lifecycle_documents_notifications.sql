ALTER TABLE documents
    ADD COLUMN checksum VARCHAR(64),
    ADD COLUMN uploaded_by UUID REFERENCES users(id),
    ADD COLUMN uploaded_at TIMESTAMPTZ;

UPDATE documents
SET uploaded_at = created_at
WHERE uploaded_at IS NULL;

ALTER TABLE documents
    ALTER COLUMN uploaded_at SET NOT NULL;

CREATE INDEX idx_documents_application_type_created
    ON documents(application_id, document_type, created_at DESC);

CREATE INDEX idx_documents_checksum
    ON documents(checksum)
    WHERE checksum IS NOT NULL;

ALTER TABLE decisions
    ADD COLUMN actor_role VARCHAR(64),
    ADD COLUMN comment TEXT,
    ADD COLUMN requested_amount NUMERIC(19, 2),
    ADD COLUMN approved_amount NUMERIC(19, 2),
    ADD COLUMN correlation_id VARCHAR(100),
    ADD COLUMN protocol_reference VARCHAR(128),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE decisions d
SET requested_amount = a.requested_amount,
    approved_amount = a.approved_amount
FROM applications a
WHERE d.application_id = a.id
  AND d.requested_amount IS NULL;

CREATE INDEX idx_decisions_type_created
    ON decisions(decision_type, created_at DESC);

ALTER TABLE notification_outbox
    ADD COLUMN event_type VARCHAR(64) NOT NULL DEFAULT 'APPLICATION_STATUS',
    ADD COLUMN locale VARCHAR(8) NOT NULL DEFAULT 'ru',
    ADD COLUMN idempotency_key VARCHAR(160);

UPDATE notification_outbox
SET idempotency_key = id::text
WHERE idempotency_key IS NULL;

ALTER TABLE notification_outbox
    DROP CONSTRAINT chk_notification_status;

ALTER TABLE notification_outbox
    ADD CONSTRAINT chk_notification_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'NOT_CONFIGURED', 'DEAD'));

CREATE UNIQUE INDEX uq_notification_outbox_idempotent_channel
    ON notification_outbox(idempotency_key, channel)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_notification_outbox_status_next_attempt
    ON notification_outbox(status, next_attempt_at, created_at);

CREATE INDEX idx_applications_status_updated
    ON applications(status, updated_at DESC, id);

CREATE INDEX idx_documents_uploaded_by_created
    ON documents(uploaded_by, created_at DESC)
    WHERE uploaded_by IS NOT NULL;

CREATE INDEX idx_ai_jobs_application_operation_status
    ON ai_processing_jobs(application_id, operation_type, status, created_at DESC);

CREATE INDEX idx_audit_logs_action_occurred
    ON audit_logs(action, occurred_at DESC);

CREATE INDEX idx_audit_logs_entity_action_occurred
    ON audit_logs(entity_type, entity_id, action, occurred_at DESC);
