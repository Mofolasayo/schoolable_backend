-- V14: Add new Team Lead rating fields for complete pillar coverage
-- Adds: adaptability_score, integrity_score, self_initiative_score

-- Add new rating columns
ALTER TABLE weekly_performance_reports
ADD COLUMN IF NOT EXISTS adaptability_score INTEGER,
ADD COLUMN IF NOT EXISTS integrity_score INTEGER,
ADD COLUMN IF NOT EXISTS self_initiative_score INTEGER;

-- Add check constraints for 1-5 scale
ALTER TABLE weekly_performance_reports
ADD CONSTRAINT check_adaptability_score CHECK (adaptability_score IS NULL OR (adaptability_score >= 1 AND adaptability_score <= 5)),
ADD CONSTRAINT check_integrity_score CHECK (integrity_score IS NULL OR (integrity_score >= 1 AND integrity_score <= 5)),
ADD CONSTRAINT check_self_initiative_score CHECK (self_initiative_score IS NULL OR (self_initiative_score >= 1 AND self_initiative_score <= 5));

-- Add comments for documentation
COMMENT ON COLUMN weekly_performance_reports.adaptability_score IS 'Team Lead rates adaptability and flexibility (1-5) - feeds into Behavioral pillar';
COMMENT ON COLUMN weekly_performance_reports.integrity_score IS 'Team Lead rates honesty and ethical behavior (1-5) - feeds into Culture Fit pillar';
COMMENT ON COLUMN weekly_performance_reports.self_initiative_score IS 'Team Lead rates proactive learning/improvement (1-5) - feeds into Growth pillar';

-- Update weekly_aura calculation to include new fields
-- Note: This recalculates weekly_aura as average of all available scores
CREATE OR REPLACE FUNCTION calculate_weekly_aura()
RETURNS TRIGGER AS $$
BEGIN
    NEW.weekly_aura := (
        COALESCE(NEW.technical_score, 3) +
        COALESCE(NEW.behavioral_score, 3) +
        COALESCE(NEW.culture_fit_score, 3) +
        COALESCE(NEW.growth_learning_score, 3)
    )::DECIMAL / 4 * 20; -- Convert 1-5 average to 0-100 scale
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Recreate trigger if needed
DROP TRIGGER IF EXISTS weekly_aura_trigger ON weekly_performance_reports;
CREATE TRIGGER weekly_aura_trigger
    BEFORE INSERT OR UPDATE ON weekly_performance_reports
    FOR EACH ROW
    EXECUTE FUNCTION calculate_weekly_aura();
