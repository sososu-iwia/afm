CREATE INDEX idx_applications_applicant_created
    ON applications(applicant_id, created_at DESC, id);

CREATE INDEX idx_applications_commission_queue
    ON applications(status, created_at DESC, id)
    WHERE status IN (
        'SUBMITTED',
        'IN_REVIEW',
        'ADDITIONAL_DOCUMENTS_REQUESTED',
        'APPROVED',
        'REJECTED'
    );

CREATE INDEX idx_applications_commission_filters
    ON applications(status, region, requested_amount, created_at DESC);

CREATE INDEX idx_applications_public_decision
    ON applications(
        (COALESCE(decision_at, updated_at)) DESC,
        application_number
    )
    WHERE status = 'APPROVED';

CREATE INDEX idx_documents_application_created
    ON documents(application_id, created_at DESC);

CREATE INDEX idx_status_history_application_created
    ON application_status_history(application_id, created_at);

CREATE INDEX idx_decisions_application_created
    ON decisions(application_id, created_at);

CREATE UNIQUE INDEX uq_generated_protocol_application
    ON generated_protocols(application_id);

CREATE INDEX idx_audit_logs_occurred
    ON audit_logs(occurred_at DESC);

CREATE INDEX idx_notification_outbox_due
    ON notification_outbox(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_ai_jobs_due
    ON ai_processing_jobs(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');
