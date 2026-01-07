ALTER TABLE profiles
ADD COLUMN IF NOT EXISTS password_hash TEXT;

ALTER TABLE profiles
ADD CONSTRAINT profiles_email_unique UNIQUE (email);
