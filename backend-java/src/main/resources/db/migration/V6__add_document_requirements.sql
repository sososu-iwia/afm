CREATE TABLE document_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_type VARCHAR(64) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    condition_expression VARCHAR(512),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    requirement_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_document_requirement_version CHECK (requirement_version > 0),
    CONSTRAINT uq_document_requirement_version
        UNIQUE (applicant_type, document_type, requirement_version)
);

CREATE INDEX idx_document_requirements_active_lookup
    ON document_requirements(applicant_type, active, requirement_version);

-- These two unconditional requirements preserve the approved month-one baseline.
-- Conditional requirements must be inserted with condition_expression and are not
-- treated as universal until a corresponding business-rule evaluator is approved.
INSERT INTO document_requirements
    (applicant_type, document_type, required, condition_expression, active, requirement_version)
VALUES
    ('GENERAL', 'IIN_CERTIFICATE', TRUE, NULL, TRUE, 1),
    ('GENERAL', 'LAND_CERTIFICATE', TRUE, NULL, TRUE, 1);

