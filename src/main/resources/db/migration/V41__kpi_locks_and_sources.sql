-- V41: KPI locking, sources, and change requests

CREATE TABLE IF NOT EXISTS kpi_period_locks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kpi_type VARCHAR(20) NOT NULL, -- team, individual
    department VARCHAR(100),
    team_lead_id UUID,
    quarter VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    locked_by UUID,
    locked_at TIMESTAMPTZ DEFAULT NOW(),
    reason TEXT,
    is_locked BOOLEAN DEFAULT TRUE,
    UNIQUE (kpi_type, department, team_lead_id, quarter, year)
);

CREATE INDEX IF NOT EXISTS idx_kpi_period_locks_quarter ON kpi_period_locks(kpi_type, quarter, year);

CREATE TABLE IF NOT EXISTS kpi_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kpi_type VARCHAR(20) NOT NULL, -- team, individual
    request_type VARCHAR(20) NOT NULL, -- CREATE, UPDATE, DELETE
    kpi_id UUID,
    payload JSONB NOT NULL,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_kpi_change_requests_status ON kpi_change_requests(status);
CREATE INDEX IF NOT EXISTS idx_kpi_change_requests_type ON kpi_change_requests(kpi_type);

ALTER TABLE team_kpis ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;
ALTER TABLE team_kpis ADD COLUMN IF NOT EXISTS progress_source VARCHAR(50);
ALTER TABLE team_kpis ADD COLUMN IF NOT EXISTS progress_config JSONB;
ALTER TABLE team_kpis ADD COLUMN IF NOT EXISTS auto_progress_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE team_kpis ADD COLUMN IF NOT EXISTS last_progress_sync_at TIMESTAMPTZ;

ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1;
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS progress_source VARCHAR(50);
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS progress_config JSONB;
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS auto_progress_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS last_progress_sync_at TIMESTAMPTZ;

ALTER TABLE weekly_kpi_progress ADD COLUMN IF NOT EXISTS source VARCHAR(30) DEFAULT 'manual';
ALTER TABLE weekly_kpi_progress ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ;
