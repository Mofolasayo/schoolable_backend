-- V8: Update Performance Management for 4-Pillar Competency Model
-- This migration revises the review structure to support holistic evaluation
-- 
-- SCORING MODEL:
-- - ALL employees: 4 pillars × 25% = 100% Aura Score
-- - Team Leads ONLY: Additional Leadership Score (25%) - tracked SEPARATELY
-- 
-- This ensures fair comparison across all employees while recognizing
-- additional leadership responsibilities for team leads.

-- ============================================
-- 1. ADD COMPETENCY SCORE COLUMNS TO REVIEWS
-- ============================================
-- Remove old collaboration_score if it exists (from any previous migration)
ALTER TABLE performance_reviews
DROP COLUMN IF EXISTS collaboration_score;

-- Add 4 core pillar scores (for ALL employees)
ALTER TABLE performance_reviews
ADD COLUMN IF NOT EXISTS technical_score DECIMAL(5,2) CHECK (technical_score >= 0 AND technical_score <= 100),
ADD COLUMN IF NOT EXISTS behavioral_score DECIMAL(5,2) CHECK (behavioral_score >= 0 AND behavioral_score <= 100),
ADD COLUMN IF NOT EXISTS culture_fit_score DECIMAL(5,2) CHECK (culture_fit_score >= 0 AND culture_fit_score <= 100),
ADD COLUMN IF NOT EXISTS growth_learning_score DECIMAL(5,2) CHECK (growth_learning_score >= 0 AND growth_learning_score <= 100);

-- Add SEPARATE leadership score (for Team Leads ONLY)
-- This is NOT part of the Aura calculation
ALTER TABLE performance_reviews
ADD COLUMN IF NOT EXISTS leadership_score DECIMAL(5,2) CHECK (leadership_score >= 0 AND leadership_score <= 100),
ADD COLUMN IF NOT EXISTS is_team_lead_review BOOLEAN DEFAULT false;

-- Add detailed comments for each pillar
ALTER TABLE performance_reviews
ADD COLUMN IF NOT EXISTS technical_comments TEXT,
ADD COLUMN IF NOT EXISTS behavioral_comments TEXT,
ADD COLUMN IF NOT EXISTS culture_fit_comments TEXT,
ADD COLUMN IF NOT EXISTS growth_learning_comments TEXT,
ADD COLUMN IF NOT EXISTS leadership_comments TEXT;

-- ============================================
-- 2. UPDATE QUARTERLY_SCORE TO BE CALCULATED
-- ============================================
-- Drop the existing quarterly_score column (CASCADE needed because quarterly_gpa depends on it)
ALTER TABLE performance_reviews DROP COLUMN IF EXISTS quarterly_score CASCADE;
-- quarterly_gpa was also dropped due to CASCADE, so we'll recreate both

-- Add quarterly_score as a generated column with 25% weight per pillar
-- 
-- AURA CALCULATION (Same for ALL employees):
-- - 4 pillars total (Technical, Behavioral, Culture, Growth)
-- - Each pillar contributes 25% to the Aura score (4 × 25% = 100%)
-- - Within each pillar, there are 5 sub-criteria (each worth 5% of that pillar)
-- 
-- Leadership Score is tracked SEPARATELY (not included in Aura)
--
ALTER TABLE performance_reviews
ADD COLUMN quarterly_score DECIMAL(5,2) GENERATED ALWAYS AS (
    CASE 
        WHEN technical_score IS NOT NULL 
             AND behavioral_score IS NOT NULL 
             AND culture_fit_score IS NOT NULL 
             AND growth_learning_score IS NOT NULL 
        THEN (
            (technical_score * 0.25) +
            (behavioral_score * 0.25) +
            (culture_fit_score * 0.25) +
            (growth_learning_score * 0.25)
        )
        ELSE NULL
    END
) STORED;

-- Recreate quarterly_gpa (convert 0-100 score to 0-5 GPA)
ALTER TABLE performance_reviews
ADD COLUMN quarterly_gpa DECIMAL(3,2) GENERATED ALWAYS AS (
    CASE 
        WHEN technical_score IS NOT NULL 
             AND behavioral_score IS NOT NULL 
             AND culture_fit_score IS NOT NULL 
             AND growth_learning_score IS NOT NULL 
        THEN (
            (technical_score * 0.25) +
            (behavioral_score * 0.25) +
            (culture_fit_score * 0.25) +
            (growth_learning_score * 0.25)
        ) / 20
        ELSE NULL
    END
) STORED;

-- ============================================
-- 3. CREATE COMPETENCY AREAS TABLE
-- ============================================
-- Define the competency framework (4 core pillars + 1 leadership supplement)
CREATE TABLE IF NOT EXISTS competency_areas (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    weight_percentage DECIMAL(5,2) DEFAULT 25.00,
    is_core_pillar BOOLEAN DEFAULT true, -- false for Leadership (team leads only)
    is_active BOOLEAN DEFAULT true,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Insert the 4 core competencies (for ALL employees) + Leadership (for Team Leads only)
INSERT INTO competency_areas (name, description, weight_percentage, is_core_pillar, sort_order)
VALUES 
    -- CORE PILLARS (4 × 25% = 100% Aura)
    ('Technical Competence', 'Job-specific skills, KPIs, and technical expertise', 25.00, true, 1),
    ('Behavioral Competence', 'Soft skills, professionalism, communication, and time management', 25.00, true, 2),
    ('Culture Fit', 'Alignment with company values, ethics, and positive contribution to work environment', 25.00, true, 3),
    ('Growth & Learning', 'Personal development, training, certifications, and knowledge sharing', 25.00, true, 4),
    -- LEADERSHIP (Separate - Team Leads ONLY - NOT part of Aura)
    ('Leadership', 'Organizational guidance, people management, decision-making, and crisis handling (Team Leads Only)', 25.00, false, 5)
ON CONFLICT (name) DO NOTHING;


-- ============================================
-- 4. CREATE COMPETENCY CRITERIA TABLE
-- ============================================
-- Detailed criteria for each competency area
CREATE TABLE IF NOT EXISTS competency_criteria (
    id BIGSERIAL PRIMARY KEY,
    competency_area_id BIGINT NOT NULL REFERENCES competency_areas(id) ON DELETE CASCADE,
    criterion_name VARCHAR(255) NOT NULL,
    description TEXT,
    measurement_method VARCHAR(50), -- 'self_assessment', 'manager_rating', 'peer_feedback', 'kpi', 'metric'
    is_required BOOLEAN DEFAULT true,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Example criteria for Technical Competence
INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Job-Specific KPIs', 'Performance against role-specific key performance indicators', 'kpi', 1
FROM competency_areas WHERE name = 'Technical Competence';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Quality of Work', 'Accuracy, thoroughness, and attention to detail', 'manager_rating', 2
FROM competency_areas WHERE name = 'Technical Competence';

-- Example criteria for Behavioral Competence
INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Communication Skills', 'Clear, professional, and effective communication', 'manager_rating', 1
FROM competency_areas WHERE name = 'Behavioral Competence';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Professionalism', 'Professional conduct, reliability, and punctuality', 'manager_rating', 2
FROM competency_areas WHERE name = 'Behavioral Competence';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Adaptability', 'Response to change and flexibility', 'manager_rating', 3
FROM competency_areas WHERE name = 'Behavioral Competence';

-- Example criteria for Culture Fit
INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Values Alignment', 'Embodies and promotes company values', 'manager_rating', 1
FROM competency_areas WHERE name = 'Culture Fit';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Team Spirit', 'Positive contribution to team morale and culture', 'peer_feedback', 2
FROM competency_areas WHERE name = 'Culture Fit';

-- Example criteria for Growth & Learning
INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Training Completion', 'Required and elective training courses completed', 'metric', 1
FROM competency_areas WHERE name = 'Growth & Learning';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Skill Development', 'New skills acquired and demonstrated', 'self_assessment', 2
FROM competency_areas WHERE name = 'Growth & Learning';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Knowledge Sharing', 'Documentation, training others, presentations', 'manager_rating', 3
FROM competency_areas WHERE name = 'Growth & Learning';

-- Example criteria for Leadership (Team Leads ONLY - separate from Aura)
INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Organizational Guidance', 'Setting direction and ensuring team alignment with company goals', 'manager_rating', 1
FROM competency_areas WHERE name = 'Leadership';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'People & Culture Leadership', 'Developing team members, fostering growth, and maintaining morale', 'direct_report_feedback', 2
FROM competency_areas WHERE name = 'Leadership';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Executive Decision-Making', 'Quality, timeliness, and impact of decisions made', 'manager_rating', 3
FROM competency_areas WHERE name = 'Leadership';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Crisis/Conflict Handling', 'Effectiveness in resolving conflicts and managing crises', 'manager_rating', 4
FROM competency_areas WHERE name = 'Leadership';

INSERT INTO competency_criteria (competency_area_id, criterion_name, description, measurement_method, sort_order)
SELECT id, 'Leadership Influence', 'Ability to inspire, motivate, and positively influence the team', '360_feedback', 5
FROM competency_areas WHERE name = 'Leadership';


-- ============================================
-- 5. CREATE COMPETENCY SCORES TABLE
-- ============================================
-- Granular scoring for each criterion within a review
CREATE TABLE IF NOT EXISTS competency_scores (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    competency_area_id BIGINT NOT NULL REFERENCES competency_areas(id),
    criterion_id BIGINT REFERENCES competency_criteria(id),
    
    -- Score for this specific criterion
    score DECIMAL(5,2) CHECK (score >= 0 AND score <= 100),
    
    -- Evidence and feedback
    evidence TEXT,
    assessor_comments TEXT,
    employee_comments TEXT,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(review_id, criterion_id)
);

CREATE INDEX idx_competency_scores_review ON competency_scores(review_id);
CREATE INDEX idx_competency_scores_area ON competency_scores(competency_area_id);

-- ============================================
-- 6. UPDATE KPIs TABLE STRUCTURE
-- ============================================
-- Link KPIs to Technical Competence area
ALTER TABLE kpis
ADD COLUMN IF NOT EXISTS competency_area_id BIGINT REFERENCES competency_areas(id);

-- Set all existing KPIs to Technical Competence
UPDATE kpis 
SET competency_area_id = (SELECT id FROM competency_areas WHERE name = 'Technical Competence')
WHERE competency_area_id IS NULL;

-- ============================================
-- 7. CREATE HELPER VIEWS
-- ============================================

-- View: Employee Competency Breakdown (4 Core Pillars)
CREATE OR REPLACE VIEW v_employee_competency_summary AS
SELECT 
    pr.employee_id,
    pr.quarter,
    pr.review_year,
    pr.technical_score,
    pr.behavioral_score,
    pr.culture_fit_score,
    pr.growth_learning_score,
    pr.leadership_score,  -- Separate for team leads
    pr.quarterly_score,
    pr.quarterly_gpa,
    ea.current_cgpa,
    ea.current_grade,
    p.full_name,
    p.employee_id AS emp_number,
    p.department,
    p.employee_level
FROM performance_reviews pr
LEFT JOIN employee_aura ea ON pr.employee_id = ea.employee_id AND pr.review_year = ea.year
JOIN profiles p ON pr.employee_id = p.id
WHERE pr.status = 'approved';

-- View: Competency Area Averages by Department (4 Core Pillars)
CREATE OR REPLACE VIEW v_department_competency_averages AS
SELECT 
    p.department,
    pr.review_year,
    pr.quarter,
    ROUND(AVG(pr.technical_score), 2) AS avg_technical,
    ROUND(AVG(pr.behavioral_score), 2) AS avg_behavioral,
    ROUND(AVG(pr.culture_fit_score), 2) AS avg_culture_fit,
    ROUND(AVG(pr.growth_learning_score), 2) AS avg_growth_learning,
    ROUND(AVG(pr.quarterly_score), 2) AS avg_overall,
    COUNT(*) AS employee_count
FROM performance_reviews pr
JOIN profiles p ON pr.employee_id = p.id
WHERE pr.status = 'approved'
GROUP BY p.department, pr.review_year, pr.quarter;

-- ============================================
-- MIGRATION COMPLETE
-- ============================================
-- 4-Pillar Competency Model implemented (Collaboration removed)
-- Tables created: 3 (competency_areas, competency_criteria, competency_scores)
-- Views created: 2
-- Columns added to performance_reviews: pillar scores + comments
