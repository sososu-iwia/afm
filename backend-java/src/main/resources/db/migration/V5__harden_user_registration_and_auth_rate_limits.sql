ALTER TABLE users
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN verified_at TIMESTAMPTZ;

UPDATE users
SET verified_at = created_at
WHERE verified_at IS NULL;

ALTER TABLE users ALTER COLUMN active SET DEFAULT FALSE;

CREATE INDEX idx_users_active_phone ON users(active, phone);

CREATE TABLE auth_rate_limit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_auth_rate_limit_key_type CHECK (key_type IN ('PHONE', 'IP'))
);

CREATE INDEX idx_auth_rate_limit_events_lookup
    ON auth_rate_limit_events(key_type, key_hash, created_at);

