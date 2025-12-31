-- V15: Add approval workflow fields to training_records
-- Adds: quarter, year, approved_by, approved_at, rejection_reason
-- Changes status default from 'in_progress' to 'pending'

-- Add new columns for quarter/year tracking
ALTER TABLE training_records
ADD COLUMN IF NOT EXISTS quarter VARCHAR(2),
ADD COLUMN IF NOT EXISTS year INTEGER;

-- Add approval workflow columns
ALTER TABLE training_records
ADD COLUMN IF NOT EXISTS approved_by UUID,
ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- Update status column default
ALTER TABLE training_records
ALTER COLUMN status SET DEFAULT 'pending';

-- Add index for efficient quarter lookups
CREATE INDEX IF NOT EXISTS idx_training_records_quarter_year 
ON training_records(employee_id, quarter, year);

-- Add index for pending certificates query
CREATE INDEX IF NOT EXISTS idx_training_records_status 
ON training_records(status, created_at);

-- Add comments for documentation
COMMENT ON COLUMN training_records.quarter IS 'Quarter (Q1, Q2, Q3, Q4) when certificate was submitted';
COMMENT ON COLUMN training_records.year IS 'Year when certificate was submitted';
COMMENT ON COLUMN training_records.approved_by IS 'UUID of super admin who approved the certificate';
COMMENT ON COLUMN training_records.approved_at IS 'Timestamp when certificate was approved';
COMMENT ON COLUMN training_records.rejection_reason IS 'Reason provided when certificate was rejected';

-- Backfill quarter/year for existing records based on created_at
UPDATE training_records
SET 
    quarter = CASE 
        WHEN EXTRACT(MONTH FROM created_at) <= 3 THEN 'Q1'
        WHEN EXTRACT(MONTH FROM created_at) <= 6 THEN 'Q2'
        WHEN EXTRACT(MONTH FROM created_at) <= 9 THEN 'Q3'
        ELSE 'Q4'
    END,
    year = EXTRACT(YEAR FROM created_at)::INTEGER
WHERE quarter IS NULL OR year IS NULL;
