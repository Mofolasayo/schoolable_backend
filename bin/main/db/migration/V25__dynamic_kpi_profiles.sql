-- ===================================
-- Dynamic Department KPI Configuration
-- Replaces hardcoded DepartmentKpiConfig.java
-- ===================================

-- Department KPI Profiles
CREATE TABLE IF NOT EXISTS department_kpi_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    department VARCHAR(100) NOT NULL UNIQUE,  -- 'Engineering', 'Marketing', etc.
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_by UUID REFERENCES profiles(id),
    updated_by UUID REFERENCES profiles(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Pillars for each department (e.g., Technical, Behavioral, Culture Fit, etc.)
CREATE TABLE IF NOT EXISTS department_pillars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL REFERENCES department_kpi_profiles(id) ON DELETE CASCADE,
    pillar_key VARCHAR(50) NOT NULL,          -- 'technical', 'behavioral', 'culture_fit', 'growth_learning', 'collaboration'
    display_name VARCHAR(100) NOT NULL,
    weight INTEGER NOT NULL CHECK (weight >= 0 AND weight <= 100),  -- Percentage 0-100
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(profile_id, pillar_key)
);

-- Metrics within each pillar
CREATE TABLE IF NOT EXISTS department_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pillar_id UUID NOT NULL REFERENCES department_pillars(id) ON DELETE CASCADE,
    metric_key VARCHAR(50) NOT NULL,           -- 'task_completion', 'code_quality', etc.
    display_name VARCHAR(100) NOT NULL,
    weight_in_pillar INTEGER NOT NULL CHECK (weight_in_pillar >= 0 AND weight_in_pillar <= 100),
    source VARCHAR(50) NOT NULL,               -- 'auto', 'team_lead', 'peer_feedback', 'admin', 'self'
    data_source VARCHAR(100),                  -- 'tasks', 'attendance', 'compliance', 'training', 'weekly_report', 'peer_ratings'
    calculation_formula TEXT,                   -- Optional: custom calculation logic description
    target_value DECIMAL(10,2),                 -- Target for auto-calculated metrics
    target_unit VARCHAR(50),                    -- 'percentage', 'count', 'days', 'hours'
    description TEXT,
    is_auto_calculated BOOLEAN DEFAULT false,   -- Whether this metric is auto-calculated
    is_active BOOLEAN DEFAULT true,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(pillar_id, metric_key)
);

-- Indexes
CREATE INDEX idx_dept_pillars_profile ON department_pillars(profile_id);
CREATE INDEX idx_dept_metrics_pillar ON department_metrics(pillar_id);
CREATE INDEX idx_dept_kpi_profiles_active ON department_kpi_profiles(is_active) WHERE is_active = true;

-- ===================================
-- Seed Default Department KPI Profiles
-- ===================================

-- Engineering Profile
INSERT INTO department_kpi_profiles (id, department, display_name, description) VALUES
    ('00000000-0000-0000-0001-000000000001', 'Engineering', 'Engineering KPI Profile', 'Performance metrics for engineering and development teams');

INSERT INTO department_pillars (id, profile_id, pillar_key, display_name, weight, sort_order) VALUES
    ('00000000-0001-0000-0001-000000000001', '00000000-0000-0000-0001-000000000001', 'technical', 'Technical Excellence', 35, 1),
    ('00000000-0001-0000-0001-000000000002', '00000000-0000-0000-0001-000000000001', 'behavioral', 'Behavioral & Soft Skills', 20, 2),
    ('00000000-0001-0000-0001-000000000003', '00000000-0000-0000-0001-000000000001', 'culture_fit', 'Culture Fit', 20, 3),
    ('00000000-0001-0000-0001-000000000004', '00000000-0000-0000-0001-000000000001', 'growth_learning', 'Growth & Learning', 15, 4),
    ('00000000-0001-0000-0001-000000000005', '00000000-0000-0000-0001-000000000001', 'collaboration', 'Collaboration', 10, 5);

-- Engineering Metrics
INSERT INTO department_metrics (pillar_id, metric_key, display_name, weight_in_pillar, source, data_source, is_auto_calculated, target_value, description) VALUES
    -- Technical (35%)
    ('00000000-0001-0000-0001-000000000001', 'task_completion', 'Task Completion Rate', 40, 'auto', 'tasks', true, 90.0, 'Percentage of assigned tasks completed on time'),
    ('00000000-0001-0000-0001-000000000001', 'code_quality', 'Code Quality Rating', 30, 'team_lead', 'weekly_report', false, 4.0, 'Team lead rating of code quality (1-5)'),
    ('00000000-0001-0000-0001-000000000001', 'on_time_delivery', 'On-Time Delivery', 30, 'auto', 'tasks', true, 85.0, 'Percentage of tasks delivered before deadline'),
    
    -- Behavioral (20%)
    ('00000000-0001-0000-0001-000000000002', 'initiative', 'Initiative', 35, 'team_lead', 'weekly_report', false, 4.0, 'Proactively identifies and solves problems'),
    ('00000000-0001-0000-0001-000000000002', 'attitude', 'Attitude Towards Work', 35, 'team_lead', 'weekly_report', false, 4.0, 'Positive attitude and professionalism'),
    ('00000000-0001-0000-0001-000000000002', 'teamwork', 'Teamwork & Collaboration', 30, 'team_lead', 'weekly_report', false, 4.0, 'Works well with team members'),
    
    -- Culture Fit (20%)
    ('00000000-0001-0000-0001-000000000003', 'policy_compliance', 'Policy Compliance', 40, 'auto', 'compliance', true, 100.0, 'Adherence to company policies'),
    ('00000000-0001-0000-0001-000000000003', 'attendance_rate', 'Attendance Rate', 35, 'auto', 'attendance', true, 95.0, 'Regular attendance and punctuality'),
    ('00000000-0001-0000-0001-000000000003', 'values_alignment', 'Values Alignment', 25, 'team_lead', 'weekly_report', false, 4.0, 'Demonstrates company values'),
    
    -- Growth & Learning (15%)
    ('00000000-0001-0000-0001-000000000004', 'certifications', 'Certifications & Training', 50, 'auto', 'training', true, 1.0, 'Completed certifications this quarter'),
    ('00000000-0001-0000-0001-000000000004', 'skill_development', 'Skill Development', 50, 'team_lead', 'weekly_report', false, 4.0, 'Actively improving skills'),
    
    -- Collaboration (10%)
    ('00000000-0001-0000-0001-000000000005', 'peer_helpfulness', 'Peer Helpfulness', 50, 'auto', 'peer_ratings', true, 4.0, 'Average helpfulness rating from colleagues'),
    ('00000000-0001-0000-0001-000000000005', 'peer_feedback_score', 'Peer Feedback Score', 50, 'auto', 'peer_feedback', true, 4.0, 'Average peer feedback rating');

-- Marketing Profile
INSERT INTO department_kpi_profiles (id, department, display_name, description) VALUES
    ('00000000-0000-0000-0002-000000000001', 'Marketing', 'Marketing KPI Profile', 'Performance metrics for marketing teams');

INSERT INTO department_pillars (id, profile_id, pillar_key, display_name, weight, sort_order) VALUES
    ('00000000-0002-0000-0001-000000000001', '00000000-0000-0000-0002-000000000001', 'technical', 'Campaign Performance', 30, 1),
    ('00000000-0002-0000-0001-000000000002', '00000000-0000-0000-0002-000000000001', 'behavioral', 'Creativity & Initiative', 25, 2),
    ('00000000-0002-0000-0001-000000000003', '00000000-0000-0000-0002-000000000001', 'culture_fit', 'Brand Alignment', 20, 3),
    ('00000000-0002-0000-0001-000000000004', '00000000-0000-0000-0002-000000000001', 'growth_learning', 'Growth & Learning', 15, 4),
    ('00000000-0002-0000-0001-000000000005', '00000000-0000-0000-0002-000000000001', 'collaboration', 'Cross-Team Collaboration', 10, 5);

-- Sales Profile
INSERT INTO department_kpi_profiles (id, department, display_name, description) VALUES
    ('00000000-0000-0000-0003-000000000001', 'Sales', 'Sales KPI Profile', 'Performance metrics for sales teams');

INSERT INTO department_pillars (id, profile_id, pillar_key, display_name, weight, sort_order) VALUES
    ('00000000-0003-0000-0001-000000000001', '00000000-0000-0000-0003-000000000001', 'technical', 'Sales Performance', 40, 1),
    ('00000000-0003-0000-0001-000000000002', '00000000-0000-0000-0003-000000000001', 'behavioral', 'Client Relations', 25, 2),
    ('00000000-0003-0000-0001-000000000003', '00000000-0000-0000-0003-000000000001', 'culture_fit', 'Culture Fit', 15, 3),
    ('00000000-0003-0000-0001-000000000004', '00000000-0000-0000-0003-000000000001', 'growth_learning', 'Professional Development', 10, 4),
    ('00000000-0003-0000-0001-000000000005', '00000000-0000-0000-0003-000000000001', 'collaboration', 'Team Synergy', 10, 5);

-- Operations Profile
INSERT INTO department_kpi_profiles (id, department, display_name, description) VALUES
    ('00000000-0000-0000-0004-000000000001', 'Operations', 'Operations KPI Profile', 'Performance metrics for operations teams');

INSERT INTO department_pillars (id, profile_id, pillar_key, display_name, weight, sort_order) VALUES
    ('00000000-0004-0000-0001-000000000001', '00000000-0000-0000-0004-000000000001', 'technical', 'Operational Efficiency', 35, 1),
    ('00000000-0004-0000-0001-000000000002', '00000000-0000-0000-0004-000000000001', 'behavioral', 'Process Adherence', 25, 2),
    ('00000000-0004-0000-0001-000000000003', '00000000-0000-0000-0004-000000000001', 'culture_fit', 'Culture Fit', 20, 3),
    ('00000000-0004-0000-0001-000000000004', '00000000-0000-0000-0004-000000000001', 'growth_learning', 'Continuous Improvement', 10, 4),
    ('00000000-0004-0000-0001-000000000005', '00000000-0000-0000-0004-000000000001', 'collaboration', 'Cross-Functional Collaboration', 10, 5);

-- HR Profile
INSERT INTO department_kpi_profiles (id, department, display_name, description) VALUES
    ('00000000-0000-0000-0005-000000000001', 'Human Resources', 'HR KPI Profile', 'Performance metrics for HR teams');

INSERT INTO department_pillars (id, profile_id, pillar_key, display_name, weight, sort_order) VALUES
    ('00000000-0005-0000-0001-000000000001', '00000000-0000-0000-0005-000000000001', 'technical', 'HR Operations', 30, 1),
    ('00000000-0005-0000-0001-000000000002', '00000000-0000-0000-0005-000000000001', 'behavioral', 'People Skills', 30, 2),
    ('00000000-0005-0000-0001-000000000003', '00000000-0000-0000-0005-000000000001', 'culture_fit', 'Culture Champion', 20, 3),
    ('00000000-0005-0000-0001-000000000004', '00000000-0000-0000-0005-000000000001', 'growth_learning', 'HR Expertise', 10, 4),
    ('00000000-0005-0000-0001-000000000005', '00000000-0000-0000-0005-000000000001', 'collaboration', 'Employee Relations', 10, 5);

-- Default Profile (for unmapped departments)
INSERT INTO department_kpi_profiles (id, department, display_name, description) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Default', 'Default KPI Profile', 'Standard performance metrics for all departments');

INSERT INTO department_pillars (id, profile_id, pillar_key, display_name, weight, sort_order) VALUES
    ('00000000-0000-0000-0001-000000000001', '00000000-0000-0000-0000-000000000001', 'technical', 'Technical Performance', 30, 1),
    ('00000000-0000-0000-0001-000000000002', '00000000-0000-0000-0000-000000000001', 'behavioral', 'Behavioral Skills', 25, 2),
    ('00000000-0000-0000-0001-000000000003', '00000000-0000-0000-0000-000000000001', 'culture_fit', 'Culture Fit', 20, 3),
    ('00000000-0000-0000-0001-000000000004', '00000000-0000-0000-0000-000000000001', 'growth_learning', 'Growth & Learning', 15, 4),
    ('00000000-0000-0000-0001-000000000005', '00000000-0000-0000-0000-000000000001', 'collaboration', 'Collaboration', 10, 5);

-- Default Metrics
INSERT INTO department_metrics (pillar_id, metric_key, display_name, weight_in_pillar, source, data_source, is_auto_calculated, target_value, description) VALUES
    -- Technical (30%)
    ('00000000-0000-0000-0001-000000000001', 'task_completion', 'Task Completion', 50, 'auto', 'tasks', true, 85.0, 'Tasks completed on time'),
    ('00000000-0000-0000-0001-000000000001', 'work_quality', 'Work Quality', 50, 'team_lead', 'weekly_report', false, 4.0, 'Quality of work delivered'),
    
    -- Behavioral (25%)
    ('00000000-0000-0000-0001-000000000002', 'initiative', 'Initiative', 35, 'team_lead', 'weekly_report', false, 4.0, 'Shows initiative'),
    ('00000000-0000-0000-0001-000000000002', 'attitude', 'Attitude', 35, 'team_lead', 'weekly_report', false, 4.0, 'Positive attitude'),
    ('00000000-0000-0000-0001-000000000002', 'teamwork', 'Teamwork', 30, 'team_lead', 'weekly_report', false, 4.0, 'Works well with others'),
    
    -- Culture Fit (20%)
    ('00000000-0000-0000-0001-000000000003', 'compliance', 'Policy Compliance', 50, 'auto', 'compliance', true, 100.0, 'Follows company policies'),
    ('00000000-0000-0000-0001-000000000003', 'attendance', 'Attendance', 50, 'auto', 'attendance', true, 95.0, 'Attendance rate'),
    
    -- Growth (15%)
    ('00000000-0000-0000-0001-000000000004', 'learning', 'Continuous Learning', 100, 'team_lead', 'weekly_report', false, 4.0, 'Actively learning new skills'),
    
    -- Collaboration (10%)
    ('00000000-0000-0000-0001-000000000005', 'peer_rating', 'Peer Collaboration Rating', 100, 'auto', 'peer_ratings', true, 4.0, 'Average peer rating');

-- Function to get automation percentage for a department
CREATE OR REPLACE FUNCTION get_department_automation_rate(dept_name VARCHAR)
RETURNS DECIMAL AS $$
DECLARE
    total_count INTEGER;
    auto_count INTEGER;
BEGIN
    SELECT COUNT(*), COUNT(*) FILTER (WHERE m.is_auto_calculated = true)
    INTO total_count, auto_count
    FROM department_metrics m
    JOIN department_pillars p ON m.pillar_id = p.id
    JOIN department_kpi_profiles d ON p.profile_id = d.id
    WHERE d.department = dept_name OR d.department = 'Default';
    
    IF total_count = 0 THEN
        RETURN 0;
    END IF;
    
    RETURN (auto_count::DECIMAL / total_count::DECIMAL) * 100;
END;
$$ LANGUAGE plpgsql;
