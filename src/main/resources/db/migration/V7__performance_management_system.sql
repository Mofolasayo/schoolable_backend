-- V7: Add Performance Management System (Allpro Aura)
-- This migration creates tables and columns for the performance review system

-- ============================================
-- 1. EXTEND PROFILES TABLE
-- ============================================
-- Add performance-related columns to existing profiles table
ALTER TABLE profiles 
ADD COLUMN IF NOT EXISTS employee_level INTEGER DEFAULT 1 CHECK (employee_level >= 1 AND employee_level <= 14),
ADD COLUMN IF NOT EXISTS cadre VARCHAR(50) DEFAULT 'Executive',
ADD COLUMN IF NOT EXISTS confirmation_status VARCHAR(20) DEFAULT 'probation' CHECK (confirmation_status IN ('probation', 'confirmed', 'extended', 'terminated')),
ADD COLUMN IF NOT EXISTS confirmation_date DATE,
ADD COLUMN IF NOT EXISTS probation_end_date DATE,
ADD COLUMN IF NOT EXISTS is_team_lead BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS base_salary DECIMAL(15,2),
ADD COLUMN IF NOT EXISTS pfp_eligible BOOLEAN DEFAULT false;

-- Create indexes for performance queries
CREATE INDEX IF NOT EXISTS idx_profiles_level ON profiles(employee_level);
CREATE INDEX IF NOT EXISTS idx_profiles_confirmation ON profiles(confirmation_status);
CREATE INDEX IF NOT EXISTS idx_profiles_team_lead ON profiles(is_team_lead) WHERE is_team_lead = true;

-- ============================================
-- 2. PERFORMANCE REVIEWS
-- ============================================
CREATE TABLE performance_reviews (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    quarter VARCHAR(10) NOT NULL, -- 'Q1', 'Q2', 'Q3', 'Q4'
    review_year INTEGER NOT NULL,
    review_date DATE NOT NULL,
    
    -- Scores (quarterly_gpa is automatically calculated)
    quarterly_score DECIMAL(5,2) NOT NULL CHECK (quarterly_score >= 0 AND quarterly_score <= 100),
    quarterly_gpa DECIMAL(3,2) GENERATED ALWAYS AS (quarterly_score / 20) STORED,
    
    -- Review metadata
    reviewer_id UUID REFERENCES profiles(id),
    status VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'submitted', 'approved', 'published')),
    comments TEXT,
    strengths TEXT,
    areas_for_improvement TEXT,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    submitted_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    
    -- Ensure one review per employee per quarter per year
    UNIQUE(employee_id, quarter, review_year)
);

-- Indexes for performance reviews
CREATE INDEX idx_performance_reviews_employee ON performance_reviews(employee_id);
CREATE INDEX idx_performance_reviews_quarter ON performance_reviews(quarter, review_year);
CREATE INDEX idx_performance_reviews_status ON performance_reviews(status);
CREATE INDEX idx_performance_reviews_reviewer ON performance_reviews(reviewer_id);

-- ============================================
-- 3. KEY PERFORMANCE INDICATORS (KPIs)
-- ============================================
CREATE TABLE kpis (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    target_value DECIMAL(10,2),
    measurement_unit VARCHAR(50), -- 'percentage', 'count', 'currency', 'boolean', etc.
    weight_percentage DECIMAL(5,2) DEFAULT 20.00 CHECK (weight_percentage > 0 AND weight_percentage <= 100),
    
    -- Period
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN DEFAULT true,
    
    -- Category/Type for grouping
    category VARCHAR(100), -- 'Technical', 'Communication', 'Leadership', etc.
    
    -- Ownership
    created_by UUID REFERENCES profiles(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for KPIs
CREATE INDEX idx_kpis_employee ON kpis(employee_id);
CREATE INDEX idx_kpis_active ON kpis(employee_id, is_active) WHERE is_active = true;
CREATE INDEX idx_kpis_category ON kpis(category);

-- ============================================
-- 4. KPI SCORES
-- ============================================
CREATE TABLE kpi_scores (
    id BIGSERIAL PRIMARY KEY,
    kpi_id BIGINT NOT NULL REFERENCES kpis(id) ON DELETE CASCADE,
    review_id BIGINT NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    
    -- Scoring
    actual_value DECIMAL(10,2),
    score_percentage DECIMAL(5,2) NOT NULL CHECK (score_percentage >= 0 AND score_percentage <= 100),
    
    -- Notes and evidence
    employee_comments TEXT,
    reviewer_comments TEXT,
    evidence_url TEXT,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- One score per KPI per review
    UNIQUE(kpi_id, review_id)
);

-- Indexes for KPI scores
CREATE INDEX idx_kpi_scores_kpi ON kpi_scores(kpi_id);
CREATE INDEX idx_kpi_scores_review ON kpi_scores(review_id);

-- ============================================
-- 5. EMPLOYEE AURA (Aggregated Performance)
-- ============================================
CREATE TABLE employee_aura (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    year INTEGER NOT NULL,
    
    -- Quarterly GPAs
    q1_gpa DECIMAL(3,2),
    q2_gpa DECIMAL(3,2),
    q3_gpa DECIMAL(3,2),
    q4_gpa DECIMAL(3,2),
    
    -- Calculated CGPA (average of available quarters)
    current_cgpa DECIMAL(3,2) GENERATED ALWAYS AS (
        CASE 
            WHEN q1_gpa IS NOT NULL AND q2_gpa IS NOT NULL AND q3_gpa IS NOT NULL AND q4_gpa IS NOT NULL 
            THEN (q1_gpa + q2_gpa + q3_gpa + q4_gpa) / 4
            WHEN q1_gpa IS NOT NULL AND q2_gpa IS NOT NULL AND q3_gpa IS NOT NULL 
            THEN (q1_gpa + q2_gpa + q3_gpa) / 3
            WHEN q1_gpa IS NOT NULL AND q2_gpa IS NOT NULL 
            THEN (q1_gpa + q2_gpa) / 2
            WHEN q1_gpa IS NOT NULL 
            THEN q1_gpa
            ELSE NULL
        END
    ) STORED,
    
    -- Performance Grade (derived from CGPA)
    current_grade VARCHAR(1) GENERATED ALWAYS AS (
        CASE 
            WHEN current_cgpa IS NULL THEN NULL
            WHEN current_cgpa >= 4.30 THEN 'A'
            WHEN current_cgpa >= 3.80 THEN 'B'
            WHEN current_cgpa >= 3.30 THEN 'C'
            WHEN current_cgpa >= 2.50 THEN 'D'
            ELSE 'F'
        END
    ) STORED,
    
    -- Promotion Eligibility Flags (updated by scheduled job)
    is_fast_track_eligible BOOLEAN DEFAULT false,
    is_vertical_promotion_eligible BOOLEAN DEFAULT false,
    is_horizontal_promotion_eligible BOOLEAN DEFAULT false,
    has_low_quarter BOOLEAN DEFAULT false, -- Flag if any quarter < 3.70
    
    -- Historical tracking
    previous_year_cgpa DECIMAL(3,2),
    
    -- Metadata
    last_calculated_at TIMESTAMPTZ DEFAULT NOW(),
    notes TEXT,
    
    -- One record per employee per year
    UNIQUE(employee_id, year)
);

-- Indexes for employee aura
CREATE INDEX idx_employee_aura_employee ON employee_aura(employee_id);
CREATE INDEX idx_employee_aura_year ON employee_aura(year);
CREATE INDEX idx_employee_aura_cgpa ON employee_aura(current_cgpa DESC NULLS LAST) WHERE current_cgpa IS NOT NULL;
CREATE INDEX idx_employee_aura_grade ON employee_aura(current_grade);
CREATE INDEX idx_employee_aura_fast_track ON employee_aura(employee_id) WHERE is_fast_track_eligible = true;
CREATE INDEX idx_employee_aura_vertical_promo ON employee_aura(employee_id) WHERE is_vertical_promotion_eligible = true;

-- ============================================
-- 6. PROMOTION HISTORY
-- ============================================
CREATE TABLE promotion_history (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Promotion details
    promotion_type VARCHAR(50) NOT NULL CHECK (promotion_type IN ('vertical', 'horizontal', 'fast_track', 'acting', 'skill_based', 'title_only', 'pay_grade')),
    from_level INTEGER NOT NULL,
    to_level INTEGER NOT NULL,
    from_cadre VARCHAR(50),
    to_cadre VARCHAR(50),
    from_job_title VARCHAR(255),
    to_job_title VARCHAR(255),
    
    -- Justification
    cgpa_at_promotion DECIMAL(3,2),
    justification TEXT NOT NULL,
    
    -- Salary impact
    old_salary DECIMAL(15,2),
    new_salary DECIMAL(15,2),
    salary_increase_percentage DECIMAL(5,2) GENERATED ALWAYS AS (
        CASE 
            WHEN old_salary IS NOT NULL AND old_salary > 0 AND new_salary IS NOT NULL
            THEN ((new_salary - old_salary) / old_salary) * 100
            ELSE NULL
        END
    ) STORED,
    
    -- Approval workflow
    recommended_by UUID REFERENCES profiles(id),
    approved_by UUID REFERENCES profiles(id),
    effective_date DATE NOT NULL,
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    approved_at TIMESTAMPTZ
);

-- Indexes for promotion history
CREATE INDEX idx_promotion_history_employee ON promotion_history(employee_id);
CREATE INDEX idx_promotion_history_type ON promotion_history(promotion_type);
CREATE INDEX idx_promotion_history_effective_date ON promotion_history(effective_date DESC);

-- ============================================
-- 7. PERFORMANCE IMPROVEMENT PLANS (PIP)
-- ============================================
CREATE TABLE pip_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- PIP period
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    duration_months INTEGER GENERATED ALWAYS AS (
        EXTRACT(YEAR FROM AGE(end_date, start_date)) * 12 + 
        EXTRACT(MONTH FROM AGE(end_date, start_date))
    ) STORED,
    
    -- Trigger information
    trigger_cgpa DECIMAL(3,2),
    trigger_quarter VARCHAR(10),
    trigger_reason TEXT NOT NULL,
    
    -- Goals and expectations
    improvement_goals TEXT NOT NULL,
    success_criteria TEXT,
    support_plan TEXT, -- Resources/training provided
    
    -- Progress tracking
    progress_notes TEXT,
    mid_review_score DECIMAL(5,2),
    mid_review_date DATE,
    
    -- Outcome
    outcome VARCHAR(50) CHECK (outcome IN ('in_progress', 'successful', 'extended', 'terminated', 'cancelled')),
    final_score DECIMAL(5,2),
    final_cgpa DECIMAL(3,2),
    outcome_notes TEXT,
    
    -- Ownership
    assigned_by UUID REFERENCES profiles(id),
    direct_supervisor UUID REFERENCES profiles(id),
    hr_partner UUID REFERENCES profiles(id),
    
    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- Indexes for PIP records
CREATE INDEX idx_pip_records_employee ON pip_records(employee_id);
CREATE INDEX idx_pip_records_active ON pip_records(employee_id, outcome) WHERE outcome = 'in_progress';
CREATE INDEX idx_pip_records_end_date ON pip_records(end_date) WHERE outcome = 'in_progress';
CREATE INDEX idx_pip_records_assigned_by ON pip_records(assigned_by);

-- ============================================
-- 8. QUARTERLY REVIEW SCHEDULE
-- ============================================
-- Track when quarterly reviews should happen
CREATE TABLE review_schedule (
    id BIGSERIAL PRIMARY KEY,
    quarter VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    review_start_date DATE NOT NULL,
    review_end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'upcoming' CHECK (status IN ('upcoming', 'active', 'completed', 'cancelled')),
    reminder_sent BOOLEAN DEFAULT false,
    
    -- Metadata
    created_by UUID REFERENCES profiles(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(quarter, year)
);

-- Index for review schedule
CREATE INDEX idx_review_schedule_status ON review_schedule(status);
CREATE INDEX idx_review_schedule_dates ON review_schedule(review_start_date, review_end_date);

-- ============================================
-- 9. FUNCTIONS FOR AUTOMATED UPDATES
-- ============================================

-- Function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply to relevant tables
CREATE TRIGGER update_performance_reviews_updated_at
    BEFORE UPDATE ON performance_reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_kpis_updated_at
    BEFORE UPDATE ON kpis
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_pip_records_updated_at
    BEFORE UPDATE ON pip_records
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 10. INSERT DEFAULT REVIEW SCHEDULE FOR 2026
-- ============================================
INSERT INTO review_schedule (quarter, year, review_start_date, review_end_date, status)
VALUES 
    ('Q1', 2026, '2026-01-01', '2026-01-14', 'upcoming'),
    ('Q2', 2026, '2026-04-01', '2026-04-14', 'upcoming'),
    ('Q3', 2026, '2026-07-01', '2026-07-14', 'upcoming'),
    ('Q4', 2026, '2026-10-01', '2026-10-14', 'upcoming')
ON CONFLICT (quarter, year) DO NOTHING;

-- ============================================
-- MIGRATION COMPLETE
-- ============================================
-- Performance Management System v1.0
-- Tables created: 8
-- Indexes created: ~25
-- Functions created: 1
-- Triggers created: 3
