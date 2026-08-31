ALTER TABLE applications
    ADD COLUMN approved_amount NUMERIC(19, 2),
    ADD COLUMN decision_at TIMESTAMPTZ;

ALTER TABLE applications
    ADD CONSTRAINT chk_applications_approved_amount
        CHECK (approved_amount IS NULL OR approved_amount >= 0);

UPDATE applications a
SET approved_amount = a.requested_amount,
    decision_at = COALESCE(
        (
            SELECT MAX(d.created_at)
            FROM decisions d
            WHERE d.application_id = a.id
              AND d.decision_type = 'APPROVED'
        ),
        a.updated_at
    )
WHERE a.status = 'APPROVED';

UPDATE applications a
SET decision_at = COALESCE(
        (
            SELECT MAX(d.created_at)
            FROM decisions d
            WHERE d.application_id = a.id
              AND d.decision_type = 'REJECTED'
        ),
        a.updated_at
    )
WHERE a.status = 'REJECTED'
  AND a.decision_at IS NULL;

CREATE INDEX idx_applications_analytics_filters
    ON applications(created_at, status, region, production_type);

CREATE INDEX idx_applications_decision_at
    ON applications(decision_at)
    WHERE decision_at IS NOT NULL;

