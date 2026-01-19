-- V52: Daily report AI context + weekly KPI context snapshots

ALTER TABLE daily_reports
    ADD COLUMN IF NOT EXISTS ai_strengths TEXT,
    ADD COLUMN IF NOT EXISTS ai_improvements TEXT,
    ADD COLUMN IF NOT EXISTS ai_aura_boost_tips TEXT;

COMMENT ON COLUMN daily_reports.ai_strengths IS 'JSON array of AI-identified strengths from daily report';
COMMENT ON COLUMN daily_reports.ai_improvements IS 'JSON array of AI-identified improvement areas from daily report';
COMMENT ON COLUMN daily_reports.ai_aura_boost_tips IS 'JSON array of AI tips to improve Aura score';

CREATE TABLE IF NOT EXISTS weekly_kpi_contexts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type VARCHAR(20) NOT NULL,
    subject_id UUID NOT NULL,
    department VARCHAR(100),
    week_number INTEGER NOT NULL,
    year INTEGER NOT NULL,
    quarter VARCHAR(10),
    context_json JSONB,
    context_text TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_weekly_kpi_context UNIQUE (subject_type, subject_id, week_number, year)
);

CREATE INDEX IF NOT EXISTS idx_weekly_kpi_context_subject
    ON weekly_kpi_contexts (subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_weekly_kpi_context_period
    ON weekly_kpi_contexts (week_number, year, quarter);

CREATE OR REPLACE FUNCTION update_weekly_kpi_context_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_weekly_kpi_context_timestamp ON weekly_kpi_contexts;
CREATE TRIGGER trigger_update_weekly_kpi_context_timestamp
    BEFORE UPDATE ON weekly_kpi_contexts
    FOR EACH ROW
    EXECUTE FUNCTION update_weekly_kpi_context_timestamp();
