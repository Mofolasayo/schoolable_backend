-- V42: Indexes for performance

CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON attendance(user_id, date);
CREATE INDEX IF NOT EXISTS idx_attendance_date_status ON attendance(date, status);
CREATE INDEX IF NOT EXISTS idx_attendance_created_at ON attendance(created_at);

CREATE INDEX IF NOT EXISTS idx_tasks_assignee_status ON tasks(assignee_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_org_status ON tasks(organization, status);
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks(created_at);
CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(due_date);

CREATE INDEX IF NOT EXISTS idx_team_kpis_team_period ON team_kpis(team_lead_id, quarter, year, is_active);
CREATE INDEX IF NOT EXISTS idx_individual_kpis_employee_period ON individual_kpis(employee_id, quarter, year, is_active);
