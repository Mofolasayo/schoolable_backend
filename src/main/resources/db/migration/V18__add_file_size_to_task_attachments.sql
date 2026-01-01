-- Add file_size column to task_attachments if it doesn't exist
-- This fixes the mismatch between entity and database schema

ALTER TABLE task_attachments ADD COLUMN IF NOT EXISTS file_size TEXT;
