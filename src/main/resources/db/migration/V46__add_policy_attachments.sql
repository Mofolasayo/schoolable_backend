-- Ensure compliance tables exist for environments that previously relied on Hibernate DDL
CREATE TABLE IF NOT EXISTS compliance_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    department VARCHAR(100),
    description TEXT,
    type VARCHAR(50) NOT NULL,
    file_url TEXT,
    file_name TEXT,
    deadline DATE,
    last_review DATE,
    next_review DATE,
    review_frequency_days INTEGER,
    is_active BOOLEAN DEFAULT true,
    created_by UUID REFERENCES profiles(id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS compliance_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id UUID NOT NULL REFERENCES compliance_policies(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'pending',
    acknowledged BOOLEAN,
    file_url TEXT,
    file_name TEXT,
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES profiles(id),
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_compliance_policies_department ON compliance_policies(department);
CREATE INDEX IF NOT EXISTS idx_compliance_policies_active ON compliance_policies(is_active);
CREATE INDEX IF NOT EXISTS idx_compliance_submissions_policy ON compliance_submissions(policy_id);
CREATE INDEX IF NOT EXISTS idx_compliance_submissions_user ON compliance_submissions(user_id);
CREATE INDEX IF NOT EXISTS idx_compliance_submissions_status ON compliance_submissions(status);

ALTER TABLE compliance_policies
    ADD COLUMN IF NOT EXISTS file_url TEXT,
    ADD COLUMN IF NOT EXISTS file_name TEXT;
