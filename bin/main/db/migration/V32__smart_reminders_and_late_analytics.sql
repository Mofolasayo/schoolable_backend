-- V32: Smart Reminders and Late Analytics Enhancement
-- Migration for smart reminders configuration

-- Smart Reminders Table
CREATE TABLE IF NOT EXISTS smart_reminders (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL, -- check_in, task_due, report_submission, peer_feedback, aura_penalty, custom
    schedule_time VARCHAR(10), -- HH:MM format
    schedule_days VARCHAR(100), -- Comma-separated: Monday,Tuesday,etc.
    timezone VARCHAR(50) DEFAULT 'Africa/Lagos',
    target_audience VARCHAR(50), -- all, pending_only, specific_team, specific_users
    message TEXT,
    channels VARCHAR(50), -- Comma-separated: push,email,sms
    is_active BOOLEAN DEFAULT TRUE,
    last_triggered TIMESTAMP WITH TIME ZONE,
    trigger_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES profiles(id)
);

-- Indexes for smart_reminders
CREATE INDEX IF NOT EXISTS idx_smart_reminders_active ON smart_reminders(is_active);
CREATE INDEX IF NOT EXISTS idx_smart_reminders_type ON smart_reminders(type);

-- Add late tracking columns to attendance table if they don't exist
-- (attendance table already has check_in time, we calculate lateness from that)

-- Table for tracking late check-in reasons more granularly
CREATE TABLE IF NOT EXISTS late_reasons (
    id SERIAL PRIMARY KEY,
    attendance_id BIGINT REFERENCES attendance(id),
    employee_id UUID REFERENCES profiles(id),
    reason_category VARCHAR(50), -- traffic, health, weather, family, work, other
    reason_detail TEXT,
    verified BOOLEAN DEFAULT FALSE,
    verified_by UUID REFERENCES profiles(id),
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for late_reasons
CREATE INDEX IF NOT EXISTS idx_late_reasons_employee ON late_reasons(employee_id);
CREATE INDEX IF NOT EXISTS idx_late_reasons_category ON late_reasons(reason_category);

-- Insert default smart reminders
INSERT INTO smart_reminders (name, description, type, schedule_time, schedule_days, timezone, target_audience, message, channels, is_active, trigger_count)
VALUES 
    ('Morning Check-in Reminder', 'Remind staff to check in if they haven''t by 9:30 AM', 'check_in', '09:30', 'Monday,Tuesday,Wednesday,Thursday,Friday', 'Africa/Lagos', 'pending_only', '⏰ Don''t forget to check in! You haven''t checked in yet today.', 'push', TRUE, 0),
    ('Task Due Reminder', 'Remind staff of tasks due within 24 hours', 'task_due', '08:00', 'Monday,Tuesday,Wednesday,Thursday,Friday', 'Africa/Lagos', 'pending_only', '📋 You have tasks due soon! Check your task list.', 'push,email', TRUE, 0),
    ('Daily Report Reminder', 'Remind about daily report submission before EOD', 'report_submission', '17:00', 'Monday,Tuesday,Wednesday,Thursday,Friday', 'Africa/Lagos', 'pending_only', '📄 Don''t forget to submit your daily report before you leave!', 'push', TRUE, 0),
    ('Peer Feedback Reminder', 'Remind staff to complete peer feedback ratings', 'peer_feedback', '15:00', 'Thursday', 'Africa/Lagos', 'pending_only', '⭐ Time to rate your colleagues! Complete your peer feedback to avoid Aura penalties.', 'push', TRUE, 0),
    ('Aura Penalty Warning', 'Warn staff about impending Aura score deductions', 'aura_penalty', '10:00', 'Friday', 'Africa/Lagos', 'pending_only', '⚠️ Warning: Your Aura score may be deducted if you don''t complete pending actions.', 'push', TRUE, 0);
