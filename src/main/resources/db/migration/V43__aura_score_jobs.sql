-- V43: Aura score job queue for async scoring and audits

CREATE TABLE IF NOT EXISTS aura_score_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, COMPLETED, FAILED, DEAD
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_run_at TIMESTAMPTZ DEFAULT NOW(),
    last_error TEXT,
    requested_by UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_aura_jobs_status_next_run ON aura_score_jobs(status, next_run_at);
CREATE INDEX IF NOT EXISTS idx_aura_jobs_type ON aura_score_jobs(job_type);
