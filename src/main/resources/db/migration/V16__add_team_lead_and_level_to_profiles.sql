-- V16: Add Team Lead designation and Employee Level to Profiles
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS is_team_lead BOOLEAN DEFAULT FALSE;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS employee_level INTEGER;
