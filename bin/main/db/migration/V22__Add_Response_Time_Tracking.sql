-- Add Communication/Response Time Tracking
-- Tracks first response time on tasks for performance metrics

ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS first_response_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN tasks.first_response_at IS 'Timestamp of first response/update by assignee after task creation';

-- Create index for response time calculations
CREATE INDEX IF NOT EXISTS idx_tasks_response_time ON tasks(assignee_id, created_at, first_response_at) 
WHERE first_response_at IS NOT NULL;
