-- V13: Add updated_at column to tasks table
-- This allows proper tracking of when tasks are completed for on-time delivery calculation

-- Add updated_at column if it doesn't exist
ALTER TABLE tasks 
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

-- Set updated_at to created_at for existing records that don't have it
UPDATE tasks SET updated_at = created_at WHERE updated_at IS NULL;

-- Create trigger to auto-update updated_at on row changes
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Drop trigger if exists and recreate
DROP TRIGGER IF EXISTS update_tasks_updated_at ON tasks;
CREATE TRIGGER update_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add index on updated_at for performance queries
CREATE INDEX IF NOT EXISTS idx_tasks_updated_at ON tasks(updated_at);
