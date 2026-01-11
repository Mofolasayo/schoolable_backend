ALTER TABLE compliance_policies
    ADD COLUMN IF NOT EXISTS file_url TEXT,
    ADD COLUMN IF NOT EXISTS file_name TEXT;
