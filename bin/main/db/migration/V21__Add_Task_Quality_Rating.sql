-- Add Task Quality Rating System
-- Allows task creators to rate assignee performance after task completion

ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS quality_rating INTEGER CHECK (quality_rating >= 1 AND quality_rating <= 5);

ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS rated_by UUID REFERENCES profiles(id);

ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS rated_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS rating_comment TEXT;

ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS rating_pending BOOLEAN DEFAULT FALSE;

-- Create index for finding tasks pending rating
CREATE INDEX IF NOT EXISTS idx_tasks_rating_pending ON tasks(created_by, rating_pending) WHERE rating_pending = TRUE;

-- Create index for calculating average ratings per assignee
CREATE INDEX IF NOT EXISTS idx_tasks_quality_rating ON tasks(assignee_id, quality_rating) WHERE quality_rating IS NOT NULL;

COMMENT ON COLUMN tasks.quality_rating IS 'Rating from 1-5 given by task creator after completion';
COMMENT ON COLUMN tasks.rating_pending IS 'Set to TRUE when task is completed, prompts creator to rate';
