ALTER TABLE profiles ADD COLUMN IF NOT EXISTS checkin_device_id VARCHAR(128);
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS checkin_device_info TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS checkin_device_registered_at TIMESTAMPTZ;
