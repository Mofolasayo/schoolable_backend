-- Add file_path column to task_attachments table if it doesn't exist
ALTER TABLE task_attachments ADD COLUMN IF NOT EXISTS file_path TEXT;
