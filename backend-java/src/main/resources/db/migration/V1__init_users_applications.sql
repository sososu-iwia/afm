CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(32) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_number VARCHAR(32) NOT NULL UNIQUE,
    applicant_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(64) NOT NULL,
    iin_or_bin VARCHAR(12),
    region VARCHAR(128),
    production_type VARCHAR(128),
    land_area NUMERIC(19, 2),
    requested_amount NUMERIC(19, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_applications_applicant_id ON applications(applicant_id);
CREATE INDEX idx_applications_status ON applications(status);
CREATE INDEX idx_applications_region ON applications(region);
CREATE INDEX idx_applications_created_at ON applications(created_at);

CREATE TABLE application_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES applications(id),
    previous_status VARCHAR(64),
    new_status VARCHAR(64) NOT NULL,
    changed_by UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(255),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_application_status_history_application_id ON application_status_history(application_id);
