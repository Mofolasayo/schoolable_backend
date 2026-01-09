-- V30: Add AI suggestions field to daily reports
-- The AI now provides actionable suggestions instead of scoring planning

ALTER TABLE daily_reports
ADD COLUMN IF NOT EXISTS ai_suggestions TEXT;

COMMENT ON COLUMN daily_reports.ai_suggestions IS 'JSON array of AI-generated priorities for next day';
