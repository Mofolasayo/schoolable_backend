-- V10: Add Weekly Performance Reports for Team Leads
-- This migration adds support for weekly rating uploads that aggregate into quarterly Aura

-- ============================================
-- 1. WEEKLY PERFORMANCE REPORTS TABLE
-- ============================================
-- Team leads submit weekly ratings for each team member
-- These are aggregated at end of quarter to calculate Aura

CREATE TABLE IF NOT EXISTS weekly_performance_reports (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES profiles(id), -- Team Lead who submitted
    
    -- Week identification
    week_number INTEGER NOT NULL CHECK (week_number >= 1 AND week_number <= 53),
    year INTEGER NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    
    -- 4 Core Pillar Scores (1-5 scale, converted to 0-100 for calculations)
    technical_score INTEGER NOT NULL CHECK (technical_score >= 1 AND technical_score <= 5),
    behavioral_score INTEGER NOT NULL CHECK (behavioral_score >= 1 AND behavioral_score <= 5),
    culture_fit_score INTEGER NOT NULL CHECK (culture_fit_score >= 1 AND culture_fit_score <= 5),
    growth_learning_score INTEGER NOT NULL CHECK (growth_learning_score >= 1 AND growth_learning_score <= 5),
    
    -- Comments for each pillar
    technical_notes TEXT,
    behavioral_notes TEXT,
    culture_fit_notes TEXT,
    growth_learning_notes TEXT,
    
    -- Weekly summary
    weekly_highlights TEXT,
    areas_for_focus TEXT,
    
    -- Calculated weekly Aura (auto-generated)
    -- Score = ((T + B + C + G) / 4) * 20 = 0-100%
    weekly_aura DECIMAL(5,2) GENERATED ALWAYS AS (
        ((technical_score + behavioral_score + culture_fit_score + growth_learning_score) / 4.0) * 20
    ) STORED,
    
    -- Status
    status VARCHAR(20) DEFAULT 'submitted' CHECK (status IN ('draft', 'submitted', 'flagged')),
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- One report per employee per week
    UNIQUE(employee_id, week_number, year)
);

-- Indexes for weekly reports
CREATE INDEX IF NOT EXISTS idx_weekly_reports_employee ON weekly_performance_reports(employee_id);
CREATE INDEX IF NOT EXISTS idx_weekly_reports_reviewer ON weekly_performance_reports(reviewer_id);
CREATE INDEX IF NOT EXISTS idx_weekly_reports_week ON weekly_performance_reports(week_number, year);
CREATE INDEX IF NOT EXISTS idx_weekly_reports_date ON weekly_performance_reports(week_start_date, week_end_date);

-- Trigger for updated_at
CREATE TRIGGER update_weekly_reports_updated_at
    BEFORE UPDATE ON weekly_performance_reports
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 2. QUARTERLY AGGREGATION VIEW
-- ============================================
-- Automatically calculates quarterly averages from weekly reports

CREATE OR REPLACE VIEW v_quarterly_performance_summary AS
SELECT 
    employee_id,
    CASE 
        WHEN week_number BETWEEN 1 AND 13 THEN 'Q1'
        WHEN week_number BETWEEN 14 AND 26 THEN 'Q2'
        WHEN week_number BETWEEN 27 AND 39 THEN 'Q3'
        ELSE 'Q4'
    END AS quarter,
    year,
    COUNT(*) AS weeks_reported,
    ROUND(AVG(technical_score * 20), 2) AS avg_technical_score,
    ROUND(AVG(behavioral_score * 20), 2) AS avg_behavioral_score,
    ROUND(AVG(culture_fit_score * 20), 2) AS avg_culture_fit_score,
    ROUND(AVG(growth_learning_score * 20), 2) AS avg_growth_learning_score,
    ROUND(AVG(weekly_aura), 2) AS avg_weekly_aura,
    ROUND(AVG(weekly_aura) / 20, 2) AS quarterly_gpa,
    CASE 
        WHEN AVG(weekly_aura) / 20 >= 4.30 THEN 'A'
        WHEN AVG(weekly_aura) / 20 >= 3.80 THEN 'B'
        WHEN AVG(weekly_aura) / 20 >= 3.30 THEN 'C'
        WHEN AVG(weekly_aura) / 20 >= 2.50 THEN 'D'
        ELSE 'F'
    END AS grade
FROM weekly_performance_reports
WHERE status = 'submitted'
GROUP BY employee_id, 
    CASE 
        WHEN week_number BETWEEN 1 AND 13 THEN 'Q1'
        WHEN week_number BETWEEN 14 AND 26 THEN 'Q2'
        WHEN week_number BETWEEN 27 AND 39 THEN 'Q3'
        ELSE 'Q4'
    END,
    year;

-- ============================================
-- 3. EMPLOYEE WEEKLY TREND VIEW
-- ============================================
-- Shows weekly performance trend for an employee

CREATE OR REPLACE VIEW v_employee_weekly_trend AS
SELECT 
    wr.employee_id,
    p.full_name,
    p.department,
    wr.week_number,
    wr.year,
    wr.week_start_date,
    wr.week_end_date,
    wr.technical_score * 20 AS technical_pct,
    wr.behavioral_score * 20 AS behavioral_pct,
    wr.culture_fit_score * 20 AS culture_fit_pct,
    wr.growth_learning_score * 20 AS growth_learning_pct,
    wr.weekly_aura,
    wr.weekly_highlights,
    wr.areas_for_focus,
    reviewer.full_name AS reviewer_name
FROM weekly_performance_reports wr
JOIN profiles p ON wr.employee_id = p.id
JOIN profiles reviewer ON wr.reviewer_id = reviewer.id
WHERE wr.status = 'submitted'
ORDER BY wr.year DESC, wr.week_number DESC;

-- ============================================
-- 4. DEPARTMENT WEEKLY SUMMARY VIEW
-- ============================================

CREATE OR REPLACE VIEW v_department_weekly_summary AS
SELECT 
    p.department,
    wr.week_number,
    wr.year,
    COUNT(*) AS employees_rated,
    ROUND(AVG(wr.weekly_aura), 2) AS avg_department_aura,
    ROUND(AVG(wr.technical_score * 20), 2) AS avg_technical,
    ROUND(AVG(wr.behavioral_score * 20), 2) AS avg_behavioral,
    ROUND(AVG(wr.culture_fit_score * 20), 2) AS avg_culture_fit,
    ROUND(AVG(wr.growth_learning_score * 20), 2) AS avg_growth
FROM weekly_performance_reports wr
JOIN profiles p ON wr.employee_id = p.id
WHERE wr.status = 'submitted'
GROUP BY p.department, wr.week_number, wr.year
ORDER BY wr.year DESC, wr.week_number DESC, p.department;

-- ============================================
-- 5. FUNCTION TO AUTO-GENERATE QUARTERLY REVIEW
-- ============================================
-- Called at end of quarter to populate performance_reviews from weekly data

CREATE OR REPLACE FUNCTION generate_quarterly_review(
    p_quarter VARCHAR(10),
    p_year INTEGER
) RETURNS INTEGER AS $$
DECLARE
    v_week_start INTEGER;
    v_week_end INTEGER;
    v_count INTEGER := 0;
BEGIN
    -- Determine week range for the quarter
    CASE p_quarter
        WHEN 'Q1' THEN v_week_start := 1; v_week_end := 13;
        WHEN 'Q2' THEN v_week_start := 14; v_week_end := 26;
        WHEN 'Q3' THEN v_week_start := 27; v_week_end := 39;
        WHEN 'Q4' THEN v_week_start := 40; v_week_end := 53;
    END CASE;

    -- Insert or update quarterly reviews from weekly averages
    INSERT INTO performance_reviews (
        employee_id,
        quarter,
        review_year,
        review_date,
        technical_score,
        behavioral_score,
        culture_fit_score,
        growth_learning_score,
        status,
        comments
    )
    SELECT 
        qs.employee_id,
        qs.quarter,
        qs.year,
        CURRENT_DATE,
        qs.avg_technical_score,
        qs.avg_behavioral_score,
        qs.avg_culture_fit_score,
        qs.avg_growth_learning_score,
        'submitted',
        'Auto-generated from ' || qs.weeks_reported || ' weekly reports'
    FROM v_quarterly_performance_summary qs
    WHERE qs.quarter = p_quarter AND qs.year = p_year
    ON CONFLICT (employee_id, quarter, review_year) 
    DO UPDATE SET
        technical_score = EXCLUDED.technical_score,
        behavioral_score = EXCLUDED.behavioral_score,
        culture_fit_score = EXCLUDED.culture_fit_score,
        growth_learning_score = EXCLUDED.growth_learning_score,
        comments = EXCLUDED.comments,
        updated_at = NOW();

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- MIGRATION COMPLETE
-- ============================================
-- Weekly Performance Reports system implemented
-- Tables created: 1 (weekly_performance_reports)
-- Views created: 3
-- Functions created: 1
-- Auto-quarterly-aggregation enabled
