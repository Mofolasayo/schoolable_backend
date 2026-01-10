-- V44: Additional indexes for attendance/task queries

CREATE INDEX IF NOT EXISTS idx_tasks_org_status_updated_at
    ON tasks(organization, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_tasks_assignee_status_updated_at
    ON tasks(assignee_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_attendance_user_status_created_at
    ON attendance(user_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_attendance_user_created_at
    ON attendance(user_id, created_at);
