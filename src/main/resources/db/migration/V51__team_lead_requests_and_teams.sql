-- Migration V51: Team lead requests + teams registry

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS team_lead_request_status VARCHAR(50) DEFAULT 'none',
    ADD COLUMN IF NOT EXISTS team_lead_requested_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES profiles(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_teams_name ON teams(name);
