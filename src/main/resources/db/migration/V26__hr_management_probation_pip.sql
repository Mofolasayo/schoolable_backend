-- Migration V26: HR Management - Probation and PIP tracking
-- Implements the Allpro Technologies Performance & Employment Level Cadre Policy

-- =====================================================
-- 1. JOB LEVELS AND GRADES (Based on Allpro Policy)
-- =====================================================

-- Job level definitions per the 14-step cadre system
CREATE TABLE IF NOT EXISTS job_levels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    level_number INTEGER NOT NULL UNIQUE,
    title VARCHAR(100) NOT NULL,
    grade INTEGER NOT NULL, -- 1-6 based on pyramid
    description TEXT,
    min_years_experience INTEGER DEFAULT 0,
    max_years_experience INTEGER,
    is_team_lead_eligible BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert the 14 job levels according to policy
INSERT INTO job_levels (level_number, title, grade, description, min_years_experience, max_years_experience, is_team_lead_eligible) VALUES
(0, 'Contract Staff', 1, 'Auxiliary & Contract Staff (SIWES, IT)', 0, 0, FALSE),
(1, 'Executive Trainee', 2, 'NYSC, Internship, Mgt Trainees', 0, 1, FALSE),
(2, 'Officer', 2, 'Entry level officer position', 0, 2, FALSE),
(3, 'Senior Officer', 3, 'Junior Executives', 1, 3, FALSE),
(4, 'Analyst', 3, 'Junior Executives', 2, 4, FALSE),
(5, 'Senior Analyst', 3, 'Mid-level position', 3, 5, TRUE),
(6, 'Associate', 3, 'Team Lead eligible', 3, 6, TRUE),
(7, 'Senior Associate', 3, 'Team Lead eligible', 4, 6, TRUE),
(8, 'Asst Manager', 4, 'Senior/Managerial Level', 5, 8, TRUE),
(9, 'Manager', 4, 'Full manager position', 6, 10, TRUE),
(10, 'Senior Manager', 4, 'Senior management', 8, 12, TRUE),
(11, 'Principal Manager', 4, 'Senior management', 10, 15, TRUE),
(12, 'Asst General Manager', 5, 'C-Suite track', 11, 18, TRUE),
(13, 'Deputy General Manager', 5, 'C-Suite', 12, 20, TRUE),
(14, 'General Manager', 6, 'Directors level', 15, NULL, TRUE)
ON CONFLICT (level_number) DO NOTHING;

-- =====================================================
-- 2. PROBATION TRACKING
-- =====================================================

CREATE TABLE IF NOT EXISTS probation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Dates
    start_date DATE NOT NULL,
    original_end_date DATE NOT NULL, -- Usually 3 months after start
    current_end_date DATE NOT NULL,  -- May be extended
    confirmed_at TIMESTAMP WITH TIME ZONE,
    
    -- Status: pending, extension_1, extension_2, extension_3, confirmed, terminated
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    
    -- Extensions tracking
    extension_count INTEGER DEFAULT 0,
    extension_reason TEXT,
    
    -- Appraisal tracking
    appraisal_scheduled_date DATE,
    appraisal_completed_date DATE,
    appraisal_score DECIMAL(5,2), -- Percentage score
    
    -- Outcome
    recommendation VARCHAR(50), -- confirm, extend, terminate
    recommendation_notes TEXT,
    
    -- Sign-offs
    supervisor_id UUID REFERENCES profiles(id),
    supervisor_approved_at TIMESTAMP WITH TIME ZONE,
    hr_approved_at TIMESTAMP WITH TIME ZONE,
    ceo_approved_at TIMESTAMP WITH TIME ZONE,
    
    -- KPIs assigned
    kpi_document_url TEXT,
    job_description_url TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES profiles(id),
    
    UNIQUE(employee_id) -- One active probation per employee
);

-- Probation history for audit
CREATE TABLE IF NOT EXISTS probation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    probation_id UUID NOT NULL REFERENCES probation_records(id) ON DELETE CASCADE,
    action VARCHAR(100) NOT NULL, -- created, extended, confirmed, terminated, appraisal_completed
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    notes TEXT,
    performed_by UUID REFERENCES profiles(id),
    performed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 3. PERFORMANCE IMPROVEMENT PLAN (PIP)
-- =====================================================

CREATE TABLE IF NOT EXISTS pip_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Dates (PIP should not exceed 3 months per policy)
    start_date DATE NOT NULL,
    end_date DATE NOT NULL, -- Max 3 months from start
    completed_at TIMESTAMP WITH TIME ZONE,
    
    -- Status: active, completed_success, completed_fail, terminated
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    
    -- Trigger information
    trigger_reason TEXT NOT NULL, -- Performance below 50%, behavioral issues, etc.
    trigger_quarter VARCHAR(10), -- Q1, Q2, Q3, Q4
    trigger_year INTEGER,
    trigger_score DECIMAL(5,2), -- The score that triggered PIP
    
    -- Goals and improvements
    improvement_goals JSONB DEFAULT '[]', -- [{goal, target, met}]
    resources_provided TEXT, -- Training, mentoring, etc.
    
    -- Check-ins
    weekly_checkins JSONB DEFAULT '[]', -- [{date, notes, progress, by}]
    
    -- Final assessment
    final_assessment_score DECIMAL(5,2),
    final_assessment_notes TEXT,
    outcome VARCHAR(50), -- improvement, termination, extension, demotion
    
    -- Approvals
    supervisor_id UUID REFERENCES profiles(id),
    hr_approved_at TIMESTAMP WITH TIME ZONE,
    ceo_approved_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES profiles(id)
);

-- PIP goals tracking
CREATE TABLE IF NOT EXISTS pip_goals (
    id BIGSERIAL PRIMARY KEY,
    pip_id BIGINT NOT NULL REFERENCES pip_records(id) ON DELETE CASCADE,
    goal_description TEXT NOT NULL,
    target_metric TEXT,
    target_value DECIMAL(10,2),
    current_value DECIMAL(10,2),
    status VARCHAR(50) DEFAULT 'in_progress', -- in_progress, met, not_met
    due_date DATE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 4. TEAM LEADS TRACKING
-- =====================================================

CREATE TABLE IF NOT EXISTS team_lead_appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Appointment info
    appointed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE, -- After probationary leadership period
    
    -- Status: acting, confirmed, ended
    status VARCHAR(50) NOT NULL DEFAULT 'acting',
    
    -- Team info
    department VARCHAR(100),
    team_name VARCHAR(200),
    team_size INTEGER,
    
    -- Performance tracking
    review_cycles_completed INTEGER DEFAULT 0,
    cgpa_at_appointment DECIMAL(3,2),
    current_cgpa DECIMAL(3,2),
    
    -- Perks (as per policy)
    perks JSONB DEFAULT '[]', -- ["workspace", "data_allowance", "retreat", etc.]
    
    -- End info
    ended_at TIMESTAMP WITH TIME ZONE,
    end_reason TEXT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 5. PROMOTION TRACKING
-- =====================================================

CREATE TABLE IF NOT EXISTS promotion_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Current position
    current_level INTEGER NOT NULL,
    current_title VARCHAR(100),
    current_grade INTEGER,
    
    -- Target position
    target_level INTEGER NOT NULL,
    target_title VARCHAR(100),
    target_grade INTEGER,
    
    -- Promotion type: vertical, horizontal, fast_track, temporary, skill_based, title_only, pay_grade
    promotion_type VARCHAR(50) NOT NULL,
    
    -- Eligibility data
    cgpa DECIMAL(3,2) NOT NULL,
    quarterly_scores JSONB, -- [{quarter, year, score}]
    meets_cgpa_threshold BOOLEAN DEFAULT FALSE,
    meets_quarterly_threshold BOOLEAN DEFAULT FALSE, -- No quarter < 3.70 for vertical
    
    -- Status: pending, approved, rejected, deferred
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    
    -- Evaluation
    manager_recommendation TEXT,
    hr_review_notes TEXT,
    panel_interview_notes TEXT,
    
    -- Approvals
    recommended_by UUID REFERENCES profiles(id),
    recommended_at TIMESTAMP WITH TIME ZONE,
    hr_reviewed_at TIMESTAMP WITH TIME ZONE,
    ceo_approved_at TIMESTAMP WITH TIME ZONE,
    
    -- Effective date
    effective_date DATE,
    salary_change BOOLEAN DEFAULT FALSE,
    new_salary_band VARCHAR(50),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- 6. ADD JOB LEVEL TO PROFILES
-- =====================================================

ALTER TABLE profiles 
ADD COLUMN IF NOT EXISTS job_level INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS grade INTEGER DEFAULT 2,
ADD COLUMN IF NOT EXISTS hire_date DATE,
ADD COLUMN IF NOT EXISTS probation_status VARCHAR(50) DEFAULT 'not_applicable',
ADD COLUMN IF NOT EXISTS is_team_lead BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS years_of_experience INTEGER DEFAULT 0;

-- =====================================================
-- 7. INDEXES
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_probation_employee ON probation_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_probation_status ON probation_records(status);
CREATE INDEX IF NOT EXISTS idx_pip_employee ON pip_records(employee_id);

-- Add status column to pip_records if it doesn't exist (V7 only created outcome column)
ALTER TABLE pip_records ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'active';

CREATE INDEX IF NOT EXISTS idx_pip_status ON pip_records(status);
CREATE INDEX IF NOT EXISTS idx_team_lead_employee ON team_lead_appointments(employee_id);
CREATE INDEX IF NOT EXISTS idx_promotion_employee ON promotion_recommendations(employee_id);
CREATE INDEX IF NOT EXISTS idx_promotion_status ON promotion_recommendations(status);
CREATE INDEX IF NOT EXISTS idx_profiles_job_level ON profiles(job_level);
CREATE INDEX IF NOT EXISTS idx_profiles_grade ON profiles(grade);
