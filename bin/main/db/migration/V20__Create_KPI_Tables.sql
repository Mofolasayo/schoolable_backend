-- V20__Create_KPI_Tables.sql
-- AI-Powered KPI System for Team Performance Tracking

-- Team KPIs: Custom KPIs defined by Team Leads
CREATE TABLE IF NOT EXISTS team_kpis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_lead_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    department VARCHAR(100),
    
    -- KPI Definition
    name VARCHAR(200) NOT NULL,
    description TEXT,
    target_value DECIMAL(10,2) NOT NULL,
    target_unit VARCHAR(50),
    weight INTEGER NOT NULL CHECK (weight > 0 AND weight <= 100),
    
    -- Period
    quarter VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    
    -- Status
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Weekly KPI Progress: Track weekly progress towards KPIs
CREATE TABLE IF NOT EXISTS weekly_kpi_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kpi_id UUID NOT NULL REFERENCES team_kpis(id) ON DELETE CASCADE,
    reported_by UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Progress
    week_number INTEGER NOT NULL,
    year INTEGER NOT NULL,
    achieved_value DECIMAL(10,2) NOT NULL,
    progress_percentage DECIMAL(5,2),
    notes TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(kpi_id, week_number, year)
);

-- AI Insights: Store AI-generated insights
CREATE TABLE IF NOT EXISTS ai_insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_lead_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    department VARCHAR(100),
    
    -- Period
    insight_type VARCHAR(20) NOT NULL, -- WEEKLY, QUARTERLY
    week_number INTEGER,
    quarter VARCHAR(10),
    year INTEGER NOT NULL,
    
    -- AI Results
    kpi_score DECIMAL(5,2),
    summary TEXT,
    insights JSONB,
    recommendations JSONB,
    risk_alerts JSONB,
    raw_ai_response JSONB,
    
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Team Quarterly Scores: Aggregated scores for super admin view
CREATE TABLE IF NOT EXISTS team_quarterly_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_lead_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    department VARCHAR(100),
    team_name VARCHAR(200),
    
    quarter VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    
    -- Scores
    kpi_achievement_score DECIMAL(5,2),
    individual_avg_score DECIMAL(5,2),
    overall_team_score DECIMAL(5,2),
    grade VARCHAR(2),
    
    ai_summary TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(team_lead_id, quarter, year)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_team_kpis_team_lead ON team_kpis(team_lead_id);
CREATE INDEX IF NOT EXISTS idx_team_kpis_quarter ON team_kpis(quarter, year);
CREATE INDEX IF NOT EXISTS idx_team_kpis_department ON team_kpis(department);

CREATE INDEX IF NOT EXISTS idx_weekly_progress_kpi ON weekly_kpi_progress(kpi_id);
CREATE INDEX IF NOT EXISTS idx_weekly_progress_week ON weekly_kpi_progress(week_number, year);

CREATE INDEX IF NOT EXISTS idx_ai_insights_team_lead ON ai_insights(team_lead_id);
CREATE INDEX IF NOT EXISTS idx_ai_insights_department ON ai_insights(department);
CREATE INDEX IF NOT EXISTS idx_ai_insights_type ON ai_insights(insight_type);

CREATE INDEX IF NOT EXISTS idx_quarterly_scores_quarter ON team_quarterly_scores(quarter, year);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_kpi_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for team_kpis
DROP TRIGGER IF EXISTS trigger_kpi_updated_at ON team_kpis;
CREATE TRIGGER trigger_kpi_updated_at
    BEFORE UPDATE ON team_kpis
    FOR EACH ROW
    EXECUTE FUNCTION update_kpi_updated_at();

-- Add comments for documentation
COMMENT ON TABLE team_kpis IS 'Custom KPIs defined by team leads for their teams';
COMMENT ON TABLE weekly_kpi_progress IS 'Weekly progress reports towards KPI targets';
COMMENT ON TABLE ai_insights IS 'AI-generated insights from Gemini analysis';
COMMENT ON TABLE team_quarterly_scores IS 'Aggregated quarterly team scores for super admin';
