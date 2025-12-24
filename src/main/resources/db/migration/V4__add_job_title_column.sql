-- Add job_title column to profiles table
-- This stores the user's actual job title/position (e.g., "Product Manager", "Senior Developer")
-- Separate from the 'role' column which is a permission level (admin/staff/employee)

ALTER TABLE profiles ADD COLUMN IF NOT EXISTS job_title TEXT;

-- Add a comment to clarify the difference between role and job_title
COMMENT ON COLUMN profiles.role IS 'Permission level: admin, staff, or employee. Used for authorization.';
COMMENT ON COLUMN profiles.job_title IS 'User''s job title/position (e.g., Product Manager, Developer). Displayed in UI.';
