#!/bin/bash
# Database Cleanup Script for Render PostgreSQL
# This will fix the NULL constraint violation issue

# Get the database URL from Render
echo "========================================="
echo "RENDER DATABASE CLEANUP"
echo "========================================="
echo ""
echo "Please copy the FULL External Database URL from Render"
echo "(starting with postgresql://...)"
echo ""
read -p "Paste the External Database URL here: " DB_URL

# Run the cleanup SQL
echo ""
echo "Running database cleanup..."
echo ""

psql "$DB_URL?sslmode=require" << EOF
-- Fix existing records with NULL core pillar scores
UPDATE weekly_performance_reports
SET technical_score = COALESCE(initiative_score, 3),
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

-- Show how many records were fixed
SELECT COUNT(*) as "Fixed Records" 
FROM weekly_performance_reports 
WHERE technical_score IS NOT NULL;

-- Verify no NULL records remain
SELECT COUNT(*) as "Remaining NULL Records" 
FROM weekly_performance_reports 
WHERE technical_score IS NULL;

EOF

echo ""
echo "========================================="
echo "CLEANUP COMPLETE!"
echo "You can now submit weekly reports again."
echo "========================================="
