-- V18: Add sub_metric_scores table for granular Aura pillar tracking
-- This table stores individual sub-metric scores for each pillar

CREATE TABLE IF NOT EXISTS sub_metric_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    pillar VARCHAR(50) NOT NULL,
    sub_metric VARCHAR(100) NOT NULL,
    score DOUBLE PRECISION NOT NULL DEFAULT 50,
    source VARCHAR(50) NOT NULL DEFAULT 'auto',
    quarter VARCHAR(5) NOT NULL,
    year INTEGER NOT NULL,
    week_number INTEGER,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    raw_data TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for efficient querying
CREATE INDEX IF NOT EXISTS idx_submetric_employee_quarter 
    ON sub_metric_scores(employee_id, quarter, year);

CREATE INDEX IF NOT EXISTS idx_submetric_pillar 
    ON sub_metric_scores(pillar, sub_metric);

CREATE INDEX IF NOT EXISTS idx_submetric_calculated 
    ON sub_metric_scores(calculated_at);

-- Unique constraint to prevent duplicate scores for same metric
CREATE UNIQUE INDEX IF NOT EXISTS idx_submetric_unique 
    ON sub_metric_scores(employee_id, pillar, sub_metric, quarter, year);

-- Comments for documentation
COMMENT ON TABLE sub_metric_scores IS 'Stores granular sub-metric scores for each Aura pillar (5 sub-metrics per pillar)';
COMMENT ON COLUMN sub_metric_scores.pillar IS 'Pillar name: technical, behavioral, culture_fit, growth, leadership';
COMMENT ON COLUMN sub_metric_scores.sub_metric IS 'Sub-metric identifier, e.g., process_execution_accuracy, teamwork_collaboration';
COMMENT ON COLUMN sub_metric_scores.score IS 'Score value from 0-100';
COMMENT ON COLUMN sub_metric_scores.source IS 'Data source: auto, team_lead, peer_feedback, admin, team_feedback';
COMMENT ON COLUMN sub_metric_scores.raw_data IS 'JSON with calculation details for audit purposes';
