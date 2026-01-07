-- V33: Add team_lead_id to profiles table
-- This allows tracking which team lead manages each employee

ALTER TABLE profiles ADD COLUMN IF NOT EXISTS team_lead_id UUID REFERENCES profiles(id);

-- Add index for faster lookups
CREATE INDEX IF NOT EXISTS idx_profiles_team_lead_id ON profiles(team_lead_id);

-- Comment
COMMENT ON COLUMN profiles.team_lead_id IS 'The UUID of the team lead who manages this employee';
