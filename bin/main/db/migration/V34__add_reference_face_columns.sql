-- V34: Add reference face columns to profiles table
-- For facial recognition features

ALTER TABLE profiles ADD COLUMN IF NOT EXISTS reference_face_url TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS reference_face_registered_at TIMESTAMP WITH TIME ZONE;

-- Comments
COMMENT ON COLUMN profiles.reference_face_url IS 'URL to the reference face image for facial recognition';
COMMENT ON COLUMN profiles.reference_face_registered_at IS 'Timestamp when the reference face was registered';
