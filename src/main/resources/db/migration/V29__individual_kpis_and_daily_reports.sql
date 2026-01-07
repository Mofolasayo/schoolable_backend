-- V29: Individual KPIs and Daily Reports
-- Adds support for:
-- 1. Individual KPIs set by team leads for each team member
-- 2. Daily reports submitted by staff, graded by AI

-- Individual KPIs table
CREATE TABLE IF NOT EXISTS individual_kpis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    set_by_id UUID NOT NULL REFERENCES profiles(id),
    department VARCHAR(100),
    
    -- KPI Definition
    name VARCHAR(200) NOT NULL,
    description TEXT,
    target_value DECIMAL(10,2) NOT NULL,
    current_value DECIMAL(10,2) DEFAULT 0,
    target_unit VARCHAR(50),
    weight INTEGER NOT NULL CHECK (weight >= 1 AND weight <= 100),
    
    -- Period
    quarter VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    
    -- Status
    is_active BOOLEAN DEFAULT true,
    achievement_percentage DECIMAL(5,2) DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for individual_kpis
CREATE INDEX IF NOT EXISTS idx_individual_kpis_employee ON individual_kpis(employee_id);
CREATE INDEX IF NOT EXISTS idx_individual_kpis_set_by ON individual_kpis(set_by_id);
CREATE INDEX IF NOT EXISTS idx_individual_kpis_period ON individual_kpis(quarter, year);
CREATE INDEX IF NOT EXISTS idx_individual_kpis_department ON individual_kpis(department, quarter, year);

-- Daily Reports table
CREATE TABLE IF NOT EXISTS daily_reports (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    
    -- Report Content
    tasks_completed TEXT NOT NULL,
    tasks_in_progress TEXT,
    blockers TEXT,
    planned_for_tomorrow TEXT,
    additional_notes TEXT,
    
    -- File Attachment
    attachment_url VARCHAR(500),
    attachment_name VARCHAR(255),
    
    -- AI Grading
    ai_score DECIMAL(5,2),
    ai_feedback TEXT,
    ai_graded_at TIMESTAMPTZ,
    kpi_alignment_score DECIMAL(5,2),
    
    -- Status & Review
    status VARCHAR(20) DEFAULT 'submitted',
    reviewed_by UUID REFERENCES profiles(id),
    reviewed_at TIMESTAMPTZ,
    reviewer_notes TEXT,
    reviewer_score DECIMAL(5,2),
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint: one report per employee per day
    CONSTRAINT unique_employee_daily_report UNIQUE (employee_id, report_date)
);

-- Indexes for daily_reports
CREATE INDEX IF NOT EXISTS idx_daily_reports_employee ON daily_reports(employee_id);
CREATE INDEX IF NOT EXISTS idx_daily_reports_date ON daily_reports(report_date);
CREATE INDEX IF NOT EXISTS idx_daily_reports_employee_date ON daily_reports(employee_id, report_date);
CREATE INDEX IF NOT EXISTS idx_daily_reports_status ON daily_reports(status);
CREATE INDEX IF NOT EXISTS idx_daily_reports_ai_pending ON daily_reports(ai_score) WHERE ai_score IS NULL;

-- Trigger for updated_at on individual_kpis
CREATE OR REPLACE FUNCTION update_individual_kpi_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_individual_kpi_timestamp ON individual_kpis;
CREATE TRIGGER trigger_update_individual_kpi_timestamp
    BEFORE UPDATE ON individual_kpis
    FOR EACH ROW
    EXECUTE FUNCTION update_individual_kpi_timestamp();

-- Trigger for updated_at on daily_reports
CREATE OR REPLACE FUNCTION update_daily_report_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_daily_report_timestamp ON daily_reports;
CREATE TRIGGER trigger_update_daily_report_timestamp
    BEFORE UPDATE ON daily_reports
    FOR EACH ROW
    EXECUTE FUNCTION update_daily_report_timestamp();

-- Comments
COMMENT ON TABLE individual_kpis IS 'Individual KPIs set by team leads for each team member. Contributes to Technical Competence pillar.';
COMMENT ON TABLE daily_reports IS 'Daily reports submitted by staff. AI-graded and contributes to Technical Competence pillar.';
