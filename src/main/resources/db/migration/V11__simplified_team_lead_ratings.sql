-- V11: Simplified Team Lead Weekly Ratings
-- Team leads now only rate 3 specific metrics; other pillars are auto-calculated

-- ============================================
-- 1. ADD NEW COLUMNS FOR SIMPLIFIED RATINGS
-- ============================================
-- These are the only 3 things team leads rate:
-- 1. Teamwork & Collaboration (contributes to Behavioral pillar)
-- 2. Initiative (contributes to Behavioral pillar)  
-- 3. Attitude Towards Work (contributes to Culture Fit pillar)

ALTER TABLE weekly_performance_reports 
    ADD COLUMN IF NOT EXISTS teamwork_collaboration_score INTEGER CHECK (teamwork_collaboration_score >= 1 AND teamwork_collaboration_score <= 5),
    ADD COLUMN IF NOT EXISTS initiative_score INTEGER CHECK (initiative_score >= 1 AND initiative_score <= 5),
    ADD COLUMN IF NOT EXISTS attitude_towards_work_score INTEGER CHECK (attitude_towards_work_score >= 1 AND attitude_towards_work_score <= 5);

-- Add team report document URL
ALTER TABLE weekly_performance_reports 
    ADD COLUMN IF NOT EXISTS team_report_url TEXT;

-- ============================================
-- 2. EMPLOYEE AURA DASHBOARD VIEW
-- ============================================
-- This view calculates the employee's current Aura score for the mobile dashboard
-- Combines auto-calculated metrics with team lead ratings

CREATE OR REPLACE VIEW v_employee_aura_dashboard AS
WITH 
-- Get latest quarter's team lead ratings
team_lead_ratings AS (
    SELECT 
        employee_id,
        AVG(teamwork_collaboration_score) AS avg_teamwork,
        AVG(initiative_score) AS avg_initiative,
        AVG(attitude_towards_work_score) AS avg_attitude,
        COUNT(*) AS weeks_rated
    FROM weekly_performance_reports
    WHERE status = 'submitted'
        AND year = EXTRACT(YEAR FROM CURRENT_DATE)
        AND week_number >= (
            CASE 
                WHEN EXTRACT(MONTH FROM CURRENT_DATE) <= 3 THEN 1
                WHEN EXTRACT(MONTH FROM CURRENT_DATE) <= 6 THEN 14
                WHEN EXTRACT(MONTH FROM CURRENT_DATE) <= 9 THEN 27
                ELSE 40
            END
        )
    GROUP BY employee_id
),
-- Calculate task-based metrics for Technical pillar
task_metrics AS (
    SELECT 
        assignee_id AS employee_id,
        -- Task completion rate
        COUNT(CASE WHEN status = 'Completed' THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) AS task_completion_rate,
        -- On-time delivery rate
        COUNT(CASE WHEN status = 'Completed' AND updated_at <= due_date THEN 1 END) * 100.0 / 
            NULLIF(COUNT(CASE WHEN status = 'Completed' THEN 1 END), 0) AS on_time_rate
    FROM tasks
    WHERE created_at >= DATE_TRUNC('quarter', CURRENT_DATE)
    GROUP BY assignee_id
),
-- Calculate attendance-based metrics for Behavioral pillar
attendance_metrics AS (
    SELECT 
        user_id AS employee_id,
        -- Punctuality rate
        COUNT(CASE WHEN status = 'present' THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) AS punctuality_rate
    FROM attendance
    WHERE created_at >= DATE_TRUNC('quarter', CURRENT_DATE)
    GROUP BY user_id
),
-- Combine all metrics into final scores
combined_scores AS (
    SELECT 
        p.id AS employee_id,
        p.full_name,
        p.department,
        p.role,
        
        -- PILLAR 1: Technical Competence (25%) - Auto-calculated
        COALESCE(tm.task_completion_rate, 50) AS technical_task_completion,
        COALESCE(tm.on_time_rate, 50) AS technical_on_time,
        
        -- PILLAR 2: Behavioral Competence (25%) - Mixed
        -- Team Lead inputs: Teamwork (5%), Initiative (5%)
        -- Auto: Professionalism/Attendance (5%), Time Management (5%), Adaptability (5%)
        COALESCE(tlr.avg_teamwork * 20, 50) AS behavioral_teamwork_score,
        COALESCE(tlr.avg_initiative * 20, 50) AS behavioral_initiative_score,
        COALESCE(am.punctuality_rate, 70) AS behavioral_professionalism_score,
        COALESCE(tm.on_time_rate, 60) AS behavioral_time_management_score,
        
        -- PILLAR 3: Culture Fit (25%) - Mixed
        -- Team Lead input: Attitude Towards Work (5%)
        -- Rest is auto/manager assessment
        COALESCE(tlr.avg_attitude * 20, 50) AS culture_attitude_score,
        
        -- PILLAR 4: Growth & Learning (25%) - Auto + Manager
        -- Based on training records, documentation, etc.
        
        -- PILLAR 5: Collaboration/Leadership (25%)
        -- Based on peer feedback, channel participation
        
        tlr.weeks_rated
        
    FROM profiles p
    LEFT JOIN team_lead_ratings tlr ON p.id = tlr.employee_id
    LEFT JOIN task_metrics tm ON p.id = tm.employee_id
    LEFT JOIN attendance_metrics am ON p.id = am.employee_id
    WHERE p.role NOT IN ('super_admin', 'admin')
)
SELECT 
    employee_id,
    full_name,
    department,
    role,
    
    -- Individual metric scores
    technical_task_completion,
    technical_on_time,
    behavioral_teamwork_score,
    behavioral_initiative_score,
    behavioral_professionalism_score,
    behavioral_time_management_score,
    culture_attitude_score,
    weeks_rated,
    
    -- PILLAR SCORES (percentage)
    -- Technical: average of task metrics
    ROUND((technical_task_completion + technical_on_time) / 2, 2) AS technical_pillar_score,
    
    -- Behavioral: weighted combo of team lead + auto metrics
    ROUND((behavioral_teamwork_score + behavioral_initiative_score + 
           behavioral_professionalism_score + behavioral_time_management_score) / 4, 2) AS behavioral_pillar_score,
    
    -- Culture Fit: attitude score (simplified for now)
    culture_attitude_score AS culture_fit_pillar_score,
    
    -- Growth & Learning: placeholder (needs training data)
    60.00 AS growth_learning_pillar_score,
    
    -- Collaboration: placeholder (needs peer feedback)
    65.00 AS collaboration_pillar_score,
    
    -- OVERALL AURA SCORE (0-100)
    ROUND(
        (
            ((technical_task_completion + technical_on_time) / 2) * 0.25 + -- Technical 25%
            ((behavioral_teamwork_score + behavioral_initiative_score + 
              behavioral_professionalism_score + behavioral_time_management_score) / 4) * 0.25 + -- Behavioral 25%
            culture_attitude_score * 0.25 + -- Culture Fit 25%
            60 * 0.125 + -- Growth placeholder (half weight)
            65 * 0.125   -- Collaboration placeholder (half weight)
        ), 2
    ) AS aura_score,
    
    -- QGPA (Aura / 20)
    ROUND(
        (
            ((technical_task_completion + technical_on_time) / 2) * 0.25 +
            ((behavioral_teamwork_score + behavioral_initiative_score + 
              behavioral_professionalism_score + behavioral_time_management_score) / 4) * 0.25 +
            culture_attitude_score * 0.25 +
            60 * 0.125 +
            65 * 0.125
        ) / 20, 2
    ) AS qgpa,
    
    -- GRADE
    CASE 
        WHEN (
            ((technical_task_completion + technical_on_time) / 2) * 0.25 +
            ((behavioral_teamwork_score + behavioral_initiative_score + 
              behavioral_professionalism_score + behavioral_time_management_score) / 4) * 0.25 +
            culture_attitude_score * 0.25 +
            60 * 0.125 +
            65 * 0.125
        ) >= 86 THEN 'A'
        WHEN (
            ((technical_task_completion + technical_on_time) / 2) * 0.25 +
            ((behavioral_teamwork_score + behavioral_initiative_score + 
              behavioral_professionalism_score + behavioral_time_management_score) / 4) * 0.25 +
            culture_attitude_score * 0.25 +
            60 * 0.125 +
            65 * 0.125
        ) >= 76 THEN 'B'
        WHEN (
            ((technical_task_completion + technical_on_time) / 2) * 0.25 +
            ((behavioral_teamwork_score + behavioral_initiative_score + 
              behavioral_professionalism_score + behavioral_time_management_score) / 4) * 0.25 +
            culture_attitude_score * 0.25 +
            60 * 0.125 +
            65 * 0.125
        ) >= 66 THEN 'C'
        WHEN (
            ((technical_task_completion + technical_on_time) / 2) * 0.25 +
            ((behavioral_teamwork_score + behavioral_initiative_score + 
              behavioral_professionalism_score + behavioral_time_management_score) / 4) * 0.25 +
            culture_attitude_score * 0.25 +
            60 * 0.125 +
            65 * 0.125
        ) >= 50 THEN 'D'
        ELSE 'F'
    END AS grade
    
FROM combined_scores;

-- ============================================
-- 3. SIMPLE API VIEW FOR CURRENT QUARTER STATS
-- ============================================

CREATE OR REPLACE VIEW v_employee_current_quarter_summary AS
SELECT 
    employee_id,
    COUNT(*) AS weeks_rated_this_quarter,
    ROUND(AVG(teamwork_collaboration_score) * 20, 2) AS avg_teamwork_pct,
    ROUND(AVG(initiative_score) * 20, 2) AS avg_initiative_pct,
    ROUND(AVG(attitude_towards_work_score) * 20, 2) AS avg_attitude_pct,
    MIN(week_start_date) AS quarter_start,
    MAX(week_end_date) AS latest_week_end
FROM weekly_performance_reports
WHERE status = 'submitted'
    AND year = EXTRACT(YEAR FROM CURRENT_DATE)
    AND week_number >= (
        CASE 
            WHEN EXTRACT(MONTH FROM CURRENT_DATE) <= 3 THEN 1
            WHEN EXTRACT(MONTH FROM CURRENT_DATE) <= 6 THEN 14
            WHEN EXTRACT(MONTH FROM CURRENT_DATE) <= 9 THEN 27
            ELSE 40
        END
    )
GROUP BY employee_id;

-- ============================================
-- COMMENTS 
-- ============================================
COMMENT ON COLUMN weekly_performance_reports.teamwork_collaboration_score IS 'Team Lead rating: How well does this person work with others? (1-5)';
COMMENT ON COLUMN weekly_performance_reports.initiative_score IS 'Team Lead rating: Does this person take proactive action? (1-5)';
COMMENT ON COLUMN weekly_performance_reports.attitude_towards_work_score IS 'Team Lead rating: Does this person maintain positive professional attitude? (1-5)';
COMMENT ON COLUMN weekly_performance_reports.team_report_url IS 'URL to the uploaded weekly team report document';
