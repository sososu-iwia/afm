CREATE SEQUENCE application_number_seq START WITH 100000;

SELECT setval(
    'application_number_seq',
    GREATEST(
        COALESCE(MAX((substring(application_number FROM '([0-9]+)$'))::BIGINT), 99999),
        99999
    ),
    true
)
FROM applications;

CREATE TABLE additional_document_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    source_submission_revision INTEGER NOT NULL,
    requested_by UUID NOT NULL REFERENCES users(id),
    requested_by_role VARCHAR(32) NOT NULL,
    comment VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    fulfilled_at TIMESTAMPTZ,
    fulfilled_by_submission_revision INTEGER,
    correlation_id VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_additional_document_request_source_revision
        CHECK (source_submission_revision > 0),
    CONSTRAINT chk_additional_document_request_status
        CHECK (status IN ('OPEN', 'FULFILLED', 'CANCELLED')),
    CONSTRAINT chk_additional_document_request_open_state
        CHECK (status <> 'OPEN' OR (
            fulfilled_at IS NULL AND fulfilled_by_submission_revision IS NULL
        )),
    CONSTRAINT chk_additional_document_request_fulfilled_state
        CHECK (status <> 'FULFILLED' OR (
            fulfilled_at IS NOT NULL
            AND fulfilled_by_submission_revision > source_submission_revision
        ))
);

CREATE TABLE additional_document_request_types (
    request_id UUID NOT NULL REFERENCES additional_document_requests(id) ON DELETE CASCADE,
    document_type VARCHAR(64) NOT NULL,
    PRIMARY KEY (request_id, document_type)
);

CREATE UNIQUE INDEX uq_additional_document_request_open_application
    ON additional_document_requests(application_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_additional_document_requests_application_created
    ON additional_document_requests(application_id, created_at);

INSERT INTO additional_document_requests (
    application_id,
    source_submission_revision,
    requested_by,
    requested_by_role,
    comment,
    status,
    created_at,
    updated_at,
    correlation_id
)
SELECT
    a.id,
    GREATEST(a.submission_revision, 1),
    latest.decided_by,
    COALESCE(latest.actor_role, u.role),
    COALESCE(NULLIF(latest.reason, ''), 'Запрошены дополнительные документы'),
    'OPEN',
    latest.created_at,
    latest.created_at,
    latest.correlation_id
FROM applications a
JOIN LATERAL (
    SELECT d.decided_by, d.actor_role, d.reason, d.created_at, d.correlation_id
    FROM decisions d
    WHERE d.application_id = a.id
      AND d.decision_type = 'ADDITIONAL_DOCUMENTS_REQUESTED'
    ORDER BY d.created_at DESC, d.id DESC
    LIMIT 1
) latest ON true
JOIN users u ON u.id = latest.decided_by
WHERE a.status = 'ADDITIONAL_DOCUMENTS_REQUESTED';
