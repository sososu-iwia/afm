ALTER TABLE ai_processing_jobs
    DROP CONSTRAINT chk_ai_job_operation,
    DROP CONSTRAINT chk_ai_job_status,
    DROP CONSTRAINT chk_ai_job_document;

ALTER TABLE ai_processing_jobs
    ADD COLUMN result_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE ai_processing_jobs
    ADD CONSTRAINT chk_ai_job_operation
        CHECK (operation_type IN ('SCORING', 'OCR', 'DUPLICATE_CHECK', 'LLM_CONCLUSION')),
    ADD CONSTRAINT chk_ai_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'FAILED_OPTIONAL', 'SKIPPED')),
    ADD CONSTRAINT chk_ai_job_document CHECK (
        (operation_type = 'OCR' AND document_id IS NOT NULL)
        OR (operation_type IN ('SCORING', 'DUPLICATE_CHECK', 'LLM_CONCLUSION') AND document_id IS NULL)
    );

ALTER TABLE application_scores
    ALTER COLUMN llm_summary DROP NOT NULL,
    ADD COLUMN model_name VARCHAR(128),
    ADD COLUMN model_version VARCHAR(128),
    ADD COLUMN model_metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE application_snapshots (
    application_id UUID PRIMARY KEY REFERENCES applications(id) ON DELETE CASCADE,
    snapshot_json JSONB NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_ai_reusable_job_application_operation
    ON ai_processing_jobs(application_id, operation_type)
    WHERE document_id IS NULL
      AND status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED_OPTIONAL', 'SKIPPED');

CREATE UNIQUE INDEX uq_ai_reusable_job_document_operation
    ON ai_processing_jobs(document_id, operation_type)
    WHERE document_id IS NOT NULL
      AND status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED_OPTIONAL', 'SKIPPED');
