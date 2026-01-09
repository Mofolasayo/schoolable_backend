-- V36__fix_null_core_pillar_scores.sql
-- This migration fixes existing weekly_performance_reports records that have NULL core pillar scores
-- by mapping the simplified ratings (Initiative, Attitude, Teamwork) to the required core pillars

-- Fix all records with NULL core pillar scores
UPDATE weekly_performance_reports
SET 
    technical_score = COALESCE(initiative_score, 3),
    behavioral_score = COALESCE(attitude_towards_work_score, 3),
    culture_fit_score = COALESCE(teamwork_collaboration_score, 3),
    growth_learning_score = ROUND((
        COALESCE(initiative_score, 3)::numeric + 
        COALESCE(attitude_towards_work_score, 3)::numeric + 
        COALESCE(teamwork_collaboration_score, 3)::numeric
    ) / 3.0)
WHERE 
    technical_score IS NULL 
    OR behavioral_score IS NULL 
    OR culture_fit_score IS NULL 
    OR growth_learning_score IS NULL;

-- Log the result
DO $$
DECLARE
    fixed_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO fixed_count 
    FROM weekly_performance_reports 
    WHERE technical_score IS NOT NULL;
    
    RAISE NOTICE 'Weekly reports cleanup: % records now have valid core pillar scores', fixed_count;
END $$;
