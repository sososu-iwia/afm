ALTER TABLE users
    ADD COLUMN account_status VARCHAR(32);

UPDATE users
SET account_status = CASE
    WHEN active = TRUE AND verified_at IS NOT NULL THEN 'ACTIVE'
    WHEN active = TRUE AND verified_at IS NULL THEN 'ACTIVE'
    ELSE 'PENDING_VERIFICATION'
END
WHERE account_status IS NULL;

UPDATE users
SET verified_at = created_at
WHERE account_status = 'ACTIVE'
  AND verified_at IS NULL;

ALTER TABLE users
    ALTER COLUMN account_status SET NOT NULL,
    ADD CONSTRAINT chk_users_account_status
        CHECK (account_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'BLOCKED', 'DISABLED'));

CREATE INDEX idx_users_account_status_role_created
    ON users(account_status, role, created_at DESC);

ALTER TABLE otp_codes
    ADD COLUMN recipient VARCHAR(64),
    ADD COLUMN maximum_attempts INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN cooldown_until TIMESTAMPTZ,
    ADD COLUMN invalidated_at TIMESTAMPTZ,
    ADD COLUMN provider_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN provider_message_id VARCHAR(128),
    ADD COLUMN last_error_code VARCHAR(64),
    ADD COLUMN request_correlation_id VARCHAR(100);

UPDATE otp_codes
SET recipient = phone
WHERE recipient IS NULL;

ALTER TABLE otp_codes
    ALTER COLUMN recipient SET NOT NULL,
    ADD CONSTRAINT chk_otp_provider_status
        CHECK (provider_status IN ('PENDING', 'SENT', 'NOT_CONFIGURED', 'DISABLED', 'FAILED')),
    ADD CONSTRAINT chk_otp_attempts
        CHECK (attempts >= 0 AND maximum_attempts > 0);

CREATE INDEX idx_otp_active_lookup
    ON otp_codes(phone, purpose, created_at DESC)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;

ALTER TABLE refresh_tokens
    ADD COLUMN family_id UUID,
    ADD COLUMN issued_at TIMESTAMPTZ,
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN revoke_reason VARCHAR(64),
    ADD COLUMN replaced_by_token_id UUID,
    ADD COLUMN user_agent VARCHAR(255),
    ADD COLUMN ip_address VARCHAR(64),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE refresh_tokens
SET family_id = id,
    issued_at = created_at
WHERE family_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL,
    ALTER COLUMN issued_at SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family
    ON refresh_tokens(family_id, revoked_at, expires_at);

CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id, revoked_at, expires_at, issued_at DESC);

ALTER TABLE audit_logs
    ADD COLUMN result VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN failure_code VARCHAR(64),
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE audit_logs
SET metadata = new_value
WHERE metadata = '{}'::jsonb
  AND new_value IS NOT NULL;

CREATE INDEX idx_audit_logs_actor_action_occurred
    ON audit_logs(actor, action, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_occurred
    ON audit_logs(occurred_at DESC);

CREATE INDEX idx_audit_logs_correlation_occurred
    ON audit_logs(correlation_id, occurred_at DESC);

ALTER TABLE applications
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN published_by UUID REFERENCES users(id);

CREATE INDEX idx_applications_public_registry_published
    ON applications(published_at DESC, decision_at DESC, application_number)
    WHERE status = 'APPROVED' AND is_public = TRUE;

CREATE UNIQUE INDEX uq_documents_storage_key
    ON documents(storage_key);

ALTER TABLE generated_protocols
    ADD COLUMN generated_at TIMESTAMPTZ,
    ADD COLUMN checksum VARCHAR(64),
    ADD COLUMN template_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    ADD COLUMN finalized_at TIMESTAMPTZ,
    ADD COLUMN finalized_by UUID REFERENCES users(id),
    ADD COLUMN protocol_number VARCHAR(128),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE generated_protocols
SET generated_at = created_at,
    finalized_at = created_at,
    finalized_by = generated_by,
    protocol_number = 'protocol-' || left(id::text, 8)
WHERE generated_at IS NULL;

ALTER TABLE generated_protocols
    ALTER COLUMN generated_at SET NOT NULL,
    ALTER COLUMN finalized_at SET NOT NULL,
    ALTER COLUMN finalized_by SET NOT NULL,
    ALTER COLUMN protocol_number SET NOT NULL;

CREATE INDEX idx_generated_protocols_finalized
    ON generated_protocols(finalized_at DESC, protocol_number);

ALTER TABLE ai_processing_jobs
    ADD COLUMN lease_owner VARCHAR(128),
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN correlation_id VARCHAR(100),
    ADD COLUMN idempotency_key VARCHAR(160);

ALTER TABLE ai_processing_jobs
    DROP CONSTRAINT chk_ai_job_status;

ALTER TABLE ai_processing_jobs
    ADD CONSTRAINT chk_ai_job_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'FAILED',
            'FAILED_RETRYABLE',
            'FAILED_TERMINAL',
            'FAILED_OPTIONAL',
            'SKIPPED'
        ));

CREATE INDEX idx_ai_jobs_status_next_lease
    ON ai_processing_jobs(status, next_attempt_at, lease_until, created_at);

CREATE UNIQUE INDEX uq_ai_jobs_idempotency_key
    ON ai_processing_jobs(idempotency_key)
    WHERE idempotency_key IS NOT NULL
      AND status IN (
          'PENDING',
          'PROCESSING',
          'COMPLETED',
          'FAILED_RETRYABLE',
          'FAILED_OPTIONAL',
          'SKIPPED'
      );

ALTER TABLE notification_outbox
    ADD COLUMN template_key VARCHAR(128),
    ADD COLUMN safe_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN provider_message_id VARCHAR(128),
    ADD COLUMN error_code VARCHAR(64),
    ADD COLUMN correlation_id VARCHAR(100),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE notification_outbox
SET template_key = event_type
WHERE template_key IS NULL;

ALTER TABLE notification_outbox
    ALTER COLUMN template_key SET NOT NULL;

CREATE INDEX idx_notification_outbox_correlation
    ON notification_outbox(correlation_id)
    WHERE correlation_id IS NOT NULL;
