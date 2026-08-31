CREATE TABLE ai_processing_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_type VARCHAR(32) NOT NULL,
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ai_job_operation CHECK (operation_type IN ('SCORING', 'OCR')),
    CONSTRAINT chk_ai_job_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_ai_job_attempts CHECK (attempt_count >= 0 AND max_attempts > 0),
    CONSTRAINT chk_ai_job_document CHECK (
        (operation_type = 'OCR' AND document_id IS NOT NULL)
        OR (operation_type = 'SCORING' AND document_id IS NULL)
    )
);

CREATE INDEX idx_ai_jobs_application_created
    ON ai_processing_jobs(application_id, operation_type, created_at DESC);
CREATE INDEX idx_ai_jobs_document_created
    ON ai_processing_jobs(document_id, operation_type, created_at DESC)
    WHERE document_id IS NOT NULL;
CREATE INDEX idx_ai_jobs_status
    ON ai_processing_jobs(status, updated_at);

CREATE TABLE application_scores (
    application_id UUID PRIMARY KEY REFERENCES applications(id) ON DELETE CASCADE,
    job_id UUID NOT NULL UNIQUE REFERENCES ai_processing_jobs(id),
    score NUMERIC(6, 2) NOT NULL,
    risk_category VARCHAR(32) NOT NULL,
    recommended_amount NUMERIC(19, 2),
    top_factors JSONB NOT NULL DEFAULT '[]'::jsonb,
    llm_summary TEXT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_ocr_results (
    document_id UUID PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    job_id UUID NOT NULL UNIQUE REFERENCES ai_processing_jobs(id),
    readable BOOLEAN NOT NULL,
    confidence NUMERIC(6, 2) NOT NULL,
    issues JSONB NOT NULL DEFAULT '[]'::jsonb,
    extracted_text TEXT NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE generated_protocols (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    generated_by UUID NOT NULL REFERENCES users(id),
    storage_key VARCHAR(512) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_generated_protocols_application_created
    ON generated_protocols(application_id, created_at DESC);
