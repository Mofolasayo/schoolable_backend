-- Emergency Data Cleanup for Weekly Reports
-- This fixes existing records that have NULL core pillar scores
-- Run this ONCE on your Render PostgreSQL database

-- Option 1: Delete broken records (SAFEST if you haven't analyzed them yet)
DELETE FROM weekly_performance_reports 
WHERE technical_score IS NULL 
   OR behavioral_score IS NULL 
   OR culture_fit_score IS NULL 
   OR growth_learning_score IS NULL;

-- Option 2: Fix broken records by mapping simplified ratings to core pillars
-- Use this if you want to preserve the records
UPDATE weekly_performance_reports
SET 
    technical_score = COALESCE(initiative_score, 3),
    behavioral_score = COALESCE(attitude_towards_work_score, 3),
    culture_fit_score = COALESCE(teamwork_collaboration_score, 3),
    growth_learning_score = ROUND((
        COALESCE(initiative_score, 3) + 
        COALESCE(attitude_towards_work_score, 3) + 
        COALESCE(teamwork_collaboration_score, 3)
    ) / 3.0)
WHERE technical_score IS NULL 
   OR behavioral_score IS NULL 
   OR culture_fit_score IS NULL 
   OR growth_learning_score IS NULL;

-- Verify cleanup
SELECT 
    id, 
    employee_id, 
    week_number, 
    year,
    technical_score, 
    behavioral_score, 
    culture_fit_score, 
    growth_learning_score,
    initiative_score,
    attitude_towards_work_score,
    teamwork_collaboration_score
FROM weekly_performance_reports
ORDER BY created_at DESC
LIMIT 10;
