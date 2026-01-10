-- ============================================================
-- V38: Phase 2 Schema Updates
-- - KPI Approval workflow fields
-- - KPI Cascading fields
-- - Task templates table
-- - Score dispute workflow
-- - Audit logging
-- ============================================================

-- Add approval workflow columns to individual_kpis
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'DRAFT';
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP;

-- Add cascading columns to individual_kpis
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS parent_kpi_id UUID;
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS cascade_level VARCHAR(20) DEFAULT 'individual';
ALTER TABLE individual_kpis ADD COLUMN IF NOT EXISTS cascade_source VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_individual_kpis_approval ON individual_kpis(approval_status);
CREATE INDEX IF NOT EXISTS idx_individual_kpis_parent ON individual_kpis(parent_kpi_id);

-- Task templates table
CREATE TABLE IF NOT EXISTS task_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    priority VARCHAR(20) DEFAULT 'Medium',
    default_days_to_complete INTEGER DEFAULT 3,
    department VARCHAR(255),
    organization VARCHAR(255),
    tags TEXT[],
    created_by UUID,
    is_active BOOLEAN DEFAULT TRUE,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_templates_dept ON task_templates(department, is_active);

-- Score dispute workflow
CREATE TABLE IF NOT EXISTS score_disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    score_type VARCHAR(50) NOT NULL, -- 'aura', 'kpi', 'pillar'
    disputed_score DECIMAL(5,2) NOT NULL,
    dispute_reason TEXT NOT NULL,
    submitted_at TIMESTAMP DEFAULT NOW(),
    status VARCHAR(20) DEFAULT 'SUBMITTED', -- SUBMITTED, UNDER_REVIEW, ADJUSTED, DENIED
    reviewed_by UUID,
    reviewed_at TIMESTAMP,
    resolution_notes TEXT,
    adjusted_score DECIMAL(5,2),
    pillar_key VARCHAR(50),
    metric_key VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_score_disputes_employee ON score_disputes(employee_id);
CREATE INDEX IF NOT EXISTS idx_score_disputes_status ON score_disputes(status);

-- Audit log for score changes
CREATE TABLE IF NOT EXISTS score_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    score_type VARCHAR(50) NOT NULL,
    old_score DECIMAL(5,2),
    new_score DECIMAL(5,2),
    change_reason TEXT NOT NULL,
    changed_by UUID,
    changed_at TIMESTAMP DEFAULT NOW(),
    source VARCHAR(50) -- 'automatic', 'manual', 'dispute', 'recalculation'
);

CREATE INDEX IF NOT EXISTS idx_score_audit_employee ON score_audit_log(employee_id);
CREATE INDEX IF NOT EXISTS idx_score_audit_at ON score_audit_log(changed_at);

-- Add training hours tracking to training_records
ALTER TABLE training_records ADD COLUMN IF NOT EXISTS actual_hours DECIMAL(5,2);
ALTER TABLE training_records ADD COLUMN IF NOT EXISTS started_at TIMESTAMP;
ALTER TABLE training_records ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
