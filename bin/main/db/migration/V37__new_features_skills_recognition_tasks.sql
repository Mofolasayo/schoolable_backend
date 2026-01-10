-- ============================================================
-- V37: Add New Feature Tables
-- - KPI History (versioning)
-- - Task Dependencies (blocked_by)
-- - Task Assignees (multiple assignees)
-- - Recurring Task Templates
-- - Recognitions (kudos system)
-- - Skills Matrix (skills, employee_skills)
-- ============================================================

-- KPI History for tracking changes
CREATE TABLE IF NOT EXISTS kpi_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kpi_id UUID NOT NULL,
    kpi_type VARCHAR(20) NOT NULL, -- 'individual', 'team', 'department'
    changed_at TIMESTAMP DEFAULT NOW(),
    previous_value TEXT,
    new_value TEXT,
    changed_by UUID,
    change_reason VARCHAR(500),
    field_changed VARCHAR(100)
);

CREATE INDEX idx_kpi_history_kpi_id ON kpi_history(kpi_id);
CREATE INDEX idx_kpi_history_changed_at ON kpi_history(changed_at);

-- Add task dependencies column
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS blocked_by_id BIGINT REFERENCES tasks(id);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurring_template_id UUID;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS is_recurring_instance BOOLEAN DEFAULT FALSE;

CREATE INDEX idx_tasks_blocked_by ON tasks(blocked_by_id);

-- Task Assignees for multiple assignees
CREATE TABLE IF NOT EXISTS task_assignees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    user_id UUID NOT NULL,
    role VARCHAR(50) DEFAULT 'contributor',
    contribution_percent INTEGER DEFAULT 0,
    assigned_at TIMESTAMP DEFAULT NOW(),
    assigned_by UUID,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_task_assignees_task ON task_assignees(task_id);
CREATE INDEX idx_task_assignees_user ON task_assignees(user_id);

-- Recurring Task Templates
CREATE TABLE IF NOT EXISTS recurring_task_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    default_priority VARCHAR(20) DEFAULT 'Medium',
    default_assignee_id UUID,
    organization VARCHAR(255),
    tags TEXT[],
    recurrence_pattern VARCHAR(20) NOT NULL, -- 'daily', 'weekly', 'biweekly', 'monthly'
    recurrence_day INTEGER,
    due_time TIME,
    days_until_due INTEGER DEFAULT 1,
    next_occurrence DATE NOT NULL,
    last_created_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_recurring_templates_active ON recurring_task_templates(is_active, next_occurrence);

-- Recognitions (Kudos System)
CREATE TABLE IF NOT EXISTS recognitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id UUID NOT NULL,
    to_user_id UUID NOT NULL,
    message TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    is_public BOOLEAN DEFAULT TRUE,
    points INTEGER DEFAULT 10,
    organization VARCHAR(255),
    department VARCHAR(255),
    task_id BIGINT,
    achievement_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_recognitions_to_user ON recognitions(to_user_id);
CREATE INDEX idx_recognitions_from_user ON recognitions(from_user_id);
CREATE INDEX idx_recognitions_public ON recognitions(is_public, created_at DESC);

-- Skills (Master list)
CREATE TABLE IF NOT EXISTS skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    organization VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by UUID
);

CREATE INDEX idx_skills_org ON skills(organization, is_active);
CREATE INDEX idx_skills_category ON skills(category, is_active);

-- Employee Skills (Proficiencies)
CREATE TABLE IF NOT EXISTS employee_skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL,
    skill_id UUID NOT NULL REFERENCES skills(id),
    proficiency_level INTEGER NOT NULL CHECK (proficiency_level BETWEEN 1 AND 5),
    is_self_assessed BOOLEAN DEFAULT TRUE,
    verified_by UUID,
    verified_at TIMESTAMP,
    years_experience DECIMAL(4,2),
    notes TEXT,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (employee_id, skill_id)
);

CREATE INDEX idx_employee_skills_employee ON employee_skills(employee_id);
CREATE INDEX idx_employee_skills_skill ON employee_skills(skill_id);
CREATE INDEX idx_employee_skills_proficiency ON employee_skills(skill_id, proficiency_level DESC);
