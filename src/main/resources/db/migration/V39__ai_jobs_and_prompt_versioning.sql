-- V39: AI job queue and prompt versioning

CREATE TABLE IF NOT EXISTS ai_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, COMPLETED, FAILED, DEAD
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_run_at TIMESTAMPTZ DEFAULT NOW(),
    last_error TEXT,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_jobs_status_next_run ON ai_jobs(status, next_run_at);
CREATE INDEX IF NOT EXISTS idx_ai_jobs_type ON ai_jobs(job_type);

CREATE TABLE IF NOT EXISTS ai_request_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES ai_jobs(id) ON DELETE SET NULL,
    prompt_version VARCHAR(20),
    model VARCHAR(100),
    request_payload JSONB,
    response_payload JSONB,
    status VARCHAR(20), -- SUCCESS, FAILED
    latency_ms INTEGER,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_request_logs_job ON ai_request_logs(job_id);
CREATE INDEX IF NOT EXISTS idx_ai_request_logs_status ON ai_request_logs(status);

ALTER TABLE ai_insights ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(20);
ALTER TABLE ai_insights ADD COLUMN IF NOT EXISTS model_used VARCHAR(100);
ALTER TABLE ai_insights ADD COLUMN IF NOT EXISTS ai_job_id UUID;
ALTER TABLE ai_insights ADD COLUMN IF NOT EXISTS ai_request_id UUID;
ALTER TABLE ai_insights ADD COLUMN IF NOT EXISTS generated_by UUID;
ALTER TABLE ai_insights ADD COLUMN IF NOT EXISTS generation_status VARCHAR(20) DEFAULT 'COMPLETED';

ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS ai_job_id UUID;
ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS ai_request_id UUID;
ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS ai_prompt_version VARCHAR(20);
ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS ai_model_used VARCHAR(100);
ALTER TABLE daily_reports ADD COLUMN IF NOT EXISTS ai_status VARCHAR(20) DEFAULT 'PENDING';

ALTER TABLE team_quarterly_scores ADD COLUMN IF NOT EXISTS ai_request_id UUID;
ALTER TABLE team_quarterly_scores ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(20);
ALTER TABLE team_quarterly_scores ADD COLUMN IF NOT EXISTS model_used VARCHAR(100);
