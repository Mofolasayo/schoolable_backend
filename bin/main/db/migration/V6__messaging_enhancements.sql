-- Add messaging enhancements for read receipts and online status

-- Add last_read_at to channel_members for read receipts
ALTER TABLE channel_members
ADD COLUMN IF NOT EXISTS last_read_at TIMESTAMP WITH TIME ZONE;

-- Add last_seen to profiles for online status tracking
ALTER TABLE profiles
ADD COLUMN IF NOT EXISTS last_seen TIMESTAMP WITH TIME ZONE;

-- Add unread_count tracking (computed at query time, but this index helps)
CREATE INDEX IF NOT EXISTS idx_messages_channel_created 
ON messages(channel_id, created_at);

-- Index for efficient online status queries
CREATE INDEX IF NOT EXISTS idx_profiles_last_seen 
ON profiles(last_seen);
