-- Add due_time to tasks for accurate overdue tracking
ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS due_time TIME;

COMMENT ON COLUMN tasks.due_time IS 'Due time of day to combine with due_date for accurate deadline tracking';

-- Create weekly peer helpfulness ratings table
CREATE TABLE IF NOT EXISTS peer_helpfulness_ratings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rater_id UUID NOT NULL REFERENCES profiles(id),
    rated_user_id UUID NOT NULL REFERENCES profiles(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    week_number INTEGER NOT NULL,
    year INTEGER NOT NULL,
    comment TEXT,
    organization VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Each user can only rate each peer once per week
    UNIQUE(rater_id, rated_user_id, week_number, year)
);

-- Index for querying ratings
CREATE INDEX IF NOT EXISTS idx_peer_helpfulness_rated_user ON peer_helpfulness_ratings(rated_user_id, week_number, year);
CREATE INDEX IF NOT EXISTS idx_peer_helpfulness_rater ON peer_helpfulness_ratings(rater_id, week_number, year);

COMMENT ON TABLE peer_helpfulness_ratings IS 'Weekly ratings of how helpful each colleague was';
