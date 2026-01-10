-- V40: Attendance scheduling, biometrics, and retention

CREATE TABLE IF NOT EXISTS work_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Africa/Lagos',
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    grace_minutes INTEGER DEFAULT 0,
    days_of_week TEXT[] NOT NULL,
    remote_allowed BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_work_schedules_active ON work_schedules(is_active);

CREATE TABLE IF NOT EXISTS employee_work_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    schedule_id UUID NOT NULL REFERENCES work_schedules(id) ON DELETE CASCADE,
    effective_start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_end_date DATE,
    is_remote BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_employee_schedule_employee ON employee_work_schedules(employee_id, effective_start_date, effective_end_date);

CREATE TABLE IF NOT EXISTS holiday_calendar (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE NOT NULL,
    name VARCHAR(100) NOT NULL,
    region VARCHAR(100),
    department VARCHAR(100),
    is_paid BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_holiday_calendar_date ON holiday_calendar(holiday_date);
CREATE INDEX IF NOT EXISTS idx_holiday_calendar_department ON holiday_calendar(department, holiday_date);

CREATE TABLE IF NOT EXISTS time_off_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    type VARCHAR(50) NOT NULL, -- PTO, SICK, REMOTE
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_time_off_employee ON time_off_requests(employee_id, status, start_date, end_date);

CREATE TABLE IF NOT EXISTS biometric_consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES profiles(id) ON DELETE CASCADE,
    consented_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consent_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    retention_days INTEGER NOT NULL DEFAULT 90,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE office_locations ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'Africa/Lagos';

ALTER TABLE attendance ADD COLUMN IF NOT EXISTS is_remote BOOLEAN DEFAULT FALSE;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS office_location_id UUID;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS schedule_id UUID;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS expected_check_in TIME;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS expected_check_out TIME;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS is_within_geofence BOOLEAN;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS distance_meters DECIMAL(10,2);
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS liveness_score DECIMAL(5,2);
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS liveness_type VARCHAR(50);
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS liveness_passed BOOLEAN;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS face_match_provider VARCHAR(50);
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS retention_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_attendance_schedule ON attendance(schedule_id);
