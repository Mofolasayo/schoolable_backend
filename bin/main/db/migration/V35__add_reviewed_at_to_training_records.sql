-- V35: Add reviewed_at column to training_records table
-- This column tracks when a training certificate was reviewed by an admin

ALTER TABLE training_records 
ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE;

-- Also add reviewed_by column if it doesn't exist
ALTER TABLE training_records 
ADD COLUMN IF NOT EXISTS reviewed_by UUID;

COMMENT ON COLUMN training_records.reviewed_at IS 'Timestamp when the training record was reviewed';
COMMENT ON COLUMN training_records.reviewed_by IS 'UUID of the admin who reviewed the training record';
