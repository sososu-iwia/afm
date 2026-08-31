ALTER TABLE applications
    ADD COLUMN activity_type VARCHAR(64) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN applicant_category VARCHAR(64) NOT NULL DEFAULT 'OTHER';

ALTER TABLE applications
    ADD CONSTRAINT chk_applications_activity_type
        CHECK (activity_type IN (
            'CROP_PRODUCTION',
            'LIVESTOCK_PRODUCTION',
            'PROCESSING',
            'STORAGE',
            'OTHER'
        )),
    ADD CONSTRAINT chk_applications_applicant_category
        CHECK (applicant_category IN (
            'INDIVIDUAL_ENTREPRENEUR',
            'LEGAL_ENTITY',
            'PEASANT_FARM',
            'COOPERATIVE',
            'OTHER'
        ));

CREATE INDEX idx_applications_public_registry
    ON applications(decision_at DESC, approved_amount DESC)
    WHERE status = 'APPROVED';

