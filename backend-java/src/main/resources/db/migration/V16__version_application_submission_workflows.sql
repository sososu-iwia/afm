ALTER TABLE applications
    ADD COLUMN submission_revision INTEGER NOT NULL DEFAULT 0;

UPDATE applications a
SET submission_revision = 1
WHERE EXISTS (SELECT 1 FROM application_snapshots s WHERE s.application_id = a.id)
   OR EXISTS (
       SELECT 1 FROM ai_processing_jobs j
       WHERE j.application_id = a.id AND j.document_id IS NULL
   )
   OR a.status IN (
       'SUBMITTED', 'IN_REVIEW', 'ADDITIONAL_DOCUMENTS_REQUESTED',
       'APPROVED', 'REJECTED'
   );

ALTER TABLE applications
    ADD CONSTRAINT chk_applications_submission_revision CHECK (submission_revision >= 0);

ALTER TABLE application_snapshots
    ADD COLUMN id UUID DEFAULT gen_random_uuid(),
    ADD COLUMN submission_revision INTEGER;

UPDATE application_snapshots s
SET submission_revision = GREATEST(a.submission_revision, 1)
FROM applications a
WHERE a.id = s.application_id
  AND s.submission_revision IS NULL;

ALTER TABLE application_snapshots
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN submission_revision SET NOT NULL,
    DROP CONSTRAINT application_snapshots_pkey,
    ADD CONSTRAINT application_snapshots_pkey PRIMARY KEY (id),
    ADD CONSTRAINT uq_application_snapshot_revision UNIQUE (application_id, submission_revision),
    ADD CONSTRAINT chk_application_snapshot_revision CHECK (submission_revision > 0);

ALTER TABLE ai_processing_jobs
    ADD COLUMN submission_revision INTEGER;

UPDATE ai_processing_jobs j
SET submission_revision = GREATEST(a.submission_revision, 1)
FROM applications a
WHERE a.id = j.application_id
  AND j.document_id IS NULL;

ALTER TABLE ai_processing_jobs
    ADD CONSTRAINT chk_ai_job_submission_revision CHECK (
        (document_id IS NOT NULL AND submission_revision IS NULL)
        OR (document_id IS NULL AND submission_revision IS NOT NULL AND submission_revision > 0)
    );

DROP INDEX uq_ai_reusable_job_application_operation;

CREATE UNIQUE INDEX uq_ai_reusable_job_application_revision_operation
    ON ai_processing_jobs(application_id, submission_revision, operation_type)
    WHERE document_id IS NULL
      AND status IN (
          'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_OPTIONAL', 'SKIPPED'
      );

CREATE INDEX idx_ai_jobs_application_revision_created
    ON ai_processing_jobs(application_id, submission_revision, operation_type, created_at DESC)
    WHERE document_id IS NULL;

ALTER TABLE application_scores
    ADD COLUMN submission_revision INTEGER;

UPDATE application_scores s
SET submission_revision = j.submission_revision
FROM ai_processing_jobs j
WHERE j.id = s.job_id
  AND j.submission_revision IS NOT NULL
  AND s.submission_revision IS NULL;

UPDATE application_scores s
SET submission_revision = GREATEST(a.submission_revision, 1)
FROM applications a
WHERE a.id = s.application_id
  AND s.submission_revision IS NULL;

ALTER TABLE application_scores
    ALTER COLUMN submission_revision SET NOT NULL,
    ADD CONSTRAINT chk_application_score_revision CHECK (submission_revision > 0);

ALTER TABLE decisions
    ADD COLUMN submission_revision INTEGER,
    ADD COLUMN scoring_job_id UUID REFERENCES ai_processing_jobs(id),
    ADD COLUMN ai_score NUMERIC(6, 2),
    ADD COLUMN ai_risk_category VARCHAR(32),
    ADD COLUMN ai_recommended_amount NUMERIC(19, 2),
    ADD COLUMN ai_top_factors JSONB,
    ADD COLUMN ai_llm_summary TEXT,
    ADD COLUMN ai_model_name VARCHAR(128),
    ADD COLUMN ai_model_version VARCHAR(128);

UPDATE decisions d
SET submission_revision = a.submission_revision
FROM applications a
WHERE a.id = d.application_id
  AND d.submission_revision IS NULL;

ALTER TABLE decisions
    ADD CONSTRAINT chk_decision_submission_revision CHECK (
        submission_revision IS NULL OR submission_revision >= 0
    );

ALTER TABLE otp_codes
    DROP CONSTRAINT chk_otp_provider_status,
    ADD CONSTRAINT chk_otp_provider_status
        CHECK (provider_status IN (
            'PENDING', 'SENT', 'DELIVERED_LOCAL', 'NOT_CONFIGURED', 'DISABLED', 'FAILED'
        ));
