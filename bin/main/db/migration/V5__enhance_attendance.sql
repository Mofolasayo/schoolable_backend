-- Enhance attendance table with face verification and detailed location data
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS accuracy DOUBLE PRECISION;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS address TEXT;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS face_match_score DOUBLE PRECISION;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS verification_status TEXT DEFAULT 'pending';
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS device_info TEXT;
ALTER TABLE attendance ADD COLUMN IF NOT EXISTS ip_address TEXT;

-- Add index for faster queries
CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON attendance(user_id, date);
CREATE INDEX IF NOT EXISTS idx_attendance_status ON attendance(status);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance(date);

-- Office locations table for geo-fencing
CREATE TABLE IF NOT EXISTS office_locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    address TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_meters INTEGER DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- Insert default office location (VGC, Lagos)
INSERT INTO office_locations (id, name, address, latitude, longitude, radius_meters)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'WorkSight HQ',
    'VGC, Lekki, Lagos, Nigeria',
    6.4427,
    3.4712,
    200
) ON CONFLICT (id) DO NOTHING;
