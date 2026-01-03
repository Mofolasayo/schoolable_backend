-- V27: Fix pip_goals foreign key type mismatch
-- The pip_records table was created in V7 with BIGSERIAL (BIGINT) id
-- V26 tried to create pip_goals with UUID foreign key which conflicts

-- First, drop pip_goals if it exists with wrong type
DROP TABLE IF EXISTS pip_goals;

-- Recreate pip_goals with correct BIGINT foreign key type
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

-- Create index for pip_goals
CREATE INDEX IF NOT EXISTS idx_pip_goals_pip ON pip_goals(pip_id);
CREATE INDEX IF NOT EXISTS idx_pip_goals_status ON pip_goals(status);

-- Also ensure the other V26 tables are created if they don't exist yet
-- (Since V26 may have partially failed)

-- job_levels should be created already, but ensure consistency
-- probation_records should be created already
-- team_lead_appointments should be created already
-- promotion_recommendations should be created already

-- Add any missing columns to profiles (in case V26 didn't complete)
ALTER TABLE profiles 
ADD COLUMN IF NOT EXISTS job_level INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS grade INTEGER DEFAULT 2,
ADD COLUMN IF NOT EXISTS hire_date DATE,
ADD COLUMN IF NOT EXISTS probation_status VARCHAR(50) DEFAULT 'not_applicable',
ADD COLUMN IF NOT EXISTS years_of_experience INTEGER DEFAULT 0;

-- Ensure indexes exist
CREATE INDEX IF NOT EXISTS idx_profiles_job_level ON profiles(job_level);
CREATE INDEX IF NOT EXISTS idx_profiles_grade ON profiles(grade);
