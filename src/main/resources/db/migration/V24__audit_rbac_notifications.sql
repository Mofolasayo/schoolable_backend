-- ===================================
-- Audit Logging System
-- ===================================

-- Audit logs table to track all changes
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,      -- 'TASK', 'PROFILE', 'ATTENDANCE', 'ANNOUNCEMENT', etc.
    entity_id VARCHAR(100) NOT NULL,         -- The ID of the modified entity
    action VARCHAR(50) NOT NULL,             -- 'CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT'
    actor_id UUID,                           -- Who made the change (null for system actions)
    actor_name VARCHAR(255),                 -- Full name for quick display
    actor_email VARCHAR(255),                -- Email for audit trail
    changes JSONB,                           -- Before/after JSON diff
    metadata JSONB,                          -- Additional context (IP, user agent, etc.)
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs(entity_type);

-- ===================================
-- Permissions System (RBAC Enhancement)
-- ===================================

-- Permission definitions
CREATE TABLE IF NOT EXISTS permissions (
    id SERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,       -- 'VIEW_ALL_STAFF', 'MANAGE_TASKS', etc.
    name VARCHAR(255) NOT NULL,              -- Human readable name
    description TEXT,
    category VARCHAR(100),                   -- 'STAFF', 'TASKS', 'COMPLIANCE', etc.
    created_at TIMESTAMP DEFAULT NOW()
);

-- Role to permissions mapping
CREATE TABLE IF NOT EXISTS role_permissions (
    id SERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,               -- 'employee', 'team_lead', 'hr_admin', 'finance_admin', 'super_admin'
    permission_id INTEGER REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(role, permission_id)
);

-- Index for quick permission lookups
CREATE INDEX idx_role_permissions_role ON role_permissions(role);

-- Insert default permissions
INSERT INTO permissions (code, name, description, category) VALUES
    -- Profile permissions
    ('VIEW_OWN_PROFILE', 'View Own Profile', 'Can view their own profile', 'PROFILE'),
    ('EDIT_OWN_PROFILE', 'Edit Own Profile', 'Can edit their own profile', 'PROFILE'),
    ('VIEW_TEAM_PROFILES', 'View Team Profiles', 'Can view profiles of team members', 'PROFILE'),
    ('VIEW_ALL_PROFILES', 'View All Profiles', 'Can view all employee profiles', 'PROFILE'),
    ('MANAGE_PROFILES', 'Manage Profiles', 'Can create, edit, delete any profile', 'PROFILE'),
    
    -- Task permissions
    ('VIEW_OWN_TASKS', 'View Own Tasks', 'Can view tasks assigned to them', 'TASKS'),
    ('CREATE_TASKS', 'Create Tasks', 'Can create and assign tasks', 'TASKS'),
    ('MANAGE_TEAM_TASKS', 'Manage Team Tasks', 'Can manage tasks for their team', 'TASKS'),
    ('MANAGE_ALL_TASKS', 'Manage All Tasks', 'Can manage any task in the organization', 'TASKS'),
    ('RATE_TASKS', 'Rate Tasks', 'Can rate completed task quality', 'TASKS'),
    
    -- Attendance permissions
    ('VIEW_OWN_ATTENDANCE', 'View Own Attendance', 'Can view their attendance history', 'ATTENDANCE'),
    ('CHECK_IN_OUT', 'Check In/Out', 'Can perform attendance check-in and check-out', 'ATTENDANCE'),
    ('VIEW_TEAM_ATTENDANCE', 'View Team Attendance', 'Can view attendance of team members', 'ATTENDANCE'),
    ('VIEW_ALL_ATTENDANCE', 'View All Attendance', 'Can view all attendance records', 'ATTENDANCE'),
    ('MANAGE_ATTENDANCE', 'Manage Attendance', 'Can edit/correct attendance records', 'ATTENDANCE'),
    
    -- Performance permissions
    ('VIEW_OWN_PERFORMANCE', 'View Own Performance', 'Can view their Aura score and feedback', 'PERFORMANCE'),
    ('VIEW_TEAM_PERFORMANCE', 'View Team Performance', 'Can view team member performance', 'PERFORMANCE'),
    ('VIEW_ALL_PERFORMANCE', 'View All Performance', 'Can view all employee performance', 'PERFORMANCE'),
    ('RATE_TEAM_MEMBERS', 'Rate Team Members', 'Can submit weekly ratings for team', 'PERFORMANCE'),
    ('SUBMIT_PEER_FEEDBACK', 'Submit Peer Feedback', 'Can submit peer feedback', 'PERFORMANCE'),
    
    -- KPI permissions
    ('VIEW_TEAM_KPIS', 'View Team KPIs', 'Can view KPIs for their department', 'KPI'),
    ('MANAGE_TEAM_KPIS', 'Manage Team KPIs', 'Can create and manage team KPIs', 'KPI'),
    ('MANAGE_ALL_KPIS', 'Manage All KPIs', 'Can manage KPIs for any department', 'KPI'),
    ('MANAGE_DEPARTMENT_PROFILES', 'Manage Department Profiles', 'Can configure department KPI profiles', 'KPI'),
    
    -- Compliance permissions
    ('VIEW_COMPLIANCE', 'View Compliance', 'Can view compliance requirements', 'COMPLIANCE'),
    ('SUBMIT_COMPLIANCE', 'Submit Compliance', 'Can submit compliance documents', 'COMPLIANCE'),
    ('MANAGE_COMPLIANCE', 'Manage Compliance', 'Can create policies and review submissions', 'COMPLIANCE'),
    
    -- Announcement permissions
    ('VIEW_ANNOUNCEMENTS', 'View Announcements', 'Can view announcements', 'ANNOUNCEMENTS'),
    ('CREATE_TEAM_ANNOUNCEMENTS', 'Create Team Announcements', 'Can create announcements for their team', 'ANNOUNCEMENTS'),
    ('CREATE_ALL_ANNOUNCEMENTS', 'Create All Announcements', 'Can create organization-wide announcements', 'ANNOUNCEMENTS'),
    
    -- Training/Certificates permissions
    ('UPLOAD_CERTIFICATES', 'Upload Certificates', 'Can upload training certificates', 'TRAINING'),
    ('VIEW_TEAM_CERTIFICATES', 'View Team Certificates', 'Can view team member certificates', 'TRAINING'),
    ('APPROVE_CERTIFICATES', 'Approve Certificates', 'Can approve/reject certificates', 'TRAINING'),
    
    -- AI Insights permissions
    ('VIEW_AI_INSIGHTS', 'View AI Insights', 'Can view AI-generated insights', 'AI'),
    ('GENERATE_AI_INSIGHTS', 'Generate AI Insights', 'Can trigger AI insight generation', 'AI'),
    
    -- Admin permissions
    ('VIEW_AUDIT_LOGS', 'View Audit Logs', 'Can view system audit logs', 'ADMIN'),
    ('MANAGE_SETTINGS', 'Manage Settings', 'Can manage platform settings', 'ADMIN'),
    ('DELETE_ACCOUNTS', 'Delete Accounts', 'Can delete user accounts', 'ADMIN')
ON CONFLICT (code) DO NOTHING;

-- Assign permissions to roles
-- Employee role
INSERT INTO role_permissions (role, permission_id)
SELECT 'employee', id FROM permissions WHERE code IN (
    'VIEW_OWN_PROFILE', 'EDIT_OWN_PROFILE',
    'VIEW_OWN_TASKS',
    'VIEW_OWN_ATTENDANCE', 'CHECK_IN_OUT',
    'VIEW_OWN_PERFORMANCE', 'SUBMIT_PEER_FEEDBACK',
    'VIEW_TEAM_KPIS',
    'VIEW_COMPLIANCE', 'SUBMIT_COMPLIANCE',
    'VIEW_ANNOUNCEMENTS',
    'UPLOAD_CERTIFICATES'
) ON CONFLICT DO NOTHING;

-- Team Lead role (includes employee permissions plus more)
INSERT INTO role_permissions (role, permission_id)
SELECT 'team_lead', id FROM permissions WHERE code IN (
    'VIEW_OWN_PROFILE', 'EDIT_OWN_PROFILE', 'VIEW_TEAM_PROFILES',
    'VIEW_OWN_TASKS', 'CREATE_TASKS', 'MANAGE_TEAM_TASKS', 'RATE_TASKS',
    'VIEW_OWN_ATTENDANCE', 'CHECK_IN_OUT', 'VIEW_TEAM_ATTENDANCE',
    'VIEW_OWN_PERFORMANCE', 'VIEW_TEAM_PERFORMANCE', 'RATE_TEAM_MEMBERS', 'SUBMIT_PEER_FEEDBACK',
    'VIEW_TEAM_KPIS', 'MANAGE_TEAM_KPIS',
    'VIEW_COMPLIANCE', 'SUBMIT_COMPLIANCE',
    'VIEW_ANNOUNCEMENTS', 'CREATE_TEAM_ANNOUNCEMENTS',
    'UPLOAD_CERTIFICATES', 'VIEW_TEAM_CERTIFICATES',
    'VIEW_AI_INSIGHTS', 'GENERATE_AI_INSIGHTS'
) ON CONFLICT DO NOTHING;

-- HR Admin role
INSERT INTO role_permissions (role, permission_id)
SELECT 'hr_admin', id FROM permissions WHERE code IN (
    'VIEW_OWN_PROFILE', 'EDIT_OWN_PROFILE', 'VIEW_ALL_PROFILES', 'MANAGE_PROFILES',
    'VIEW_OWN_TASKS', 'VIEW_OWN_ATTENDANCE', 'VIEW_ALL_ATTENDANCE', 'MANAGE_ATTENDANCE',
    'VIEW_OWN_PERFORMANCE', 'VIEW_ALL_PERFORMANCE',
    'VIEW_TEAM_KPIS',
    'VIEW_COMPLIANCE', 'MANAGE_COMPLIANCE',
    'VIEW_ANNOUNCEMENTS', 'CREATE_ALL_ANNOUNCEMENTS',
    'APPROVE_CERTIFICATES',
    'VIEW_AI_INSIGHTS',
    'VIEW_AUDIT_LOGS'
) ON CONFLICT DO NOTHING;

-- Finance Admin role
INSERT INTO role_permissions (role, permission_id)
SELECT 'finance_admin', id FROM permissions WHERE code IN (
    'VIEW_OWN_PROFILE', 'EDIT_OWN_PROFILE', 'VIEW_ALL_PROFILES',
    'VIEW_OWN_TASKS', 'VIEW_OWN_ATTENDANCE',
    'VIEW_OWN_PERFORMANCE', 'VIEW_ALL_PERFORMANCE',
    'VIEW_TEAM_KPIS',
    'VIEW_COMPLIANCE',
    'VIEW_ANNOUNCEMENTS'
) ON CONFLICT DO NOTHING;

-- Super Admin role (all permissions)
INSERT INTO role_permissions (role, permission_id)
SELECT 'super_admin', id FROM permissions
ON CONFLICT DO NOTHING;

-- Also grant super_admin all permissions to 'admin' role for backward compatibility
INSERT INTO role_permissions (role, permission_id)
SELECT 'admin', id FROM permissions
ON CONFLICT DO NOTHING;

-- ===================================
-- Device Tokens for Push Notifications
-- ===================================

CREATE TABLE IF NOT EXISTS device_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL,          -- 'ios', 'android', 'web'
    device_info JSONB,                       -- Device details
    is_active BOOLEAN DEFAULT true,
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(user_id, token)
);

CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);
CREATE INDEX idx_device_tokens_active ON device_tokens(is_active) WHERE is_active = true;

-- ===================================
-- Notification History
-- ===================================

CREATE TABLE IF NOT EXISTS notification_history (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    data JSONB,                              -- Payload data
    type VARCHAR(50),                        -- 'TASK', 'ANNOUNCEMENT', 'MESSAGE', etc.
    reference_id VARCHAR(100),               -- ID of related entity
    is_read BOOLEAN DEFAULT false,
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    read_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_notification_history_user ON notification_history(user_id);
CREATE INDEX idx_notification_history_unread ON notification_history(user_id, is_read) WHERE is_read = false;
