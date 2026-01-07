-- V19: Add additional rating fields to peer_feedback table
-- These fields support the enhanced sub-metric calculations

-- Behavioral sub-metrics
ALTER TABLE peer_feedback ADD COLUMN IF NOT EXISTS adaptability_rating INTEGER;

-- Culture Fit sub-metrics
ALTER TABLE peer_feedback ADD COLUMN IF NOT EXISTS values_rating INTEGER;
ALTER TABLE peer_feedback ADD COLUMN IF NOT EXISTS accountability_rating INTEGER;

-- Growth sub-metrics
ALTER TABLE peer_feedback ADD COLUMN IF NOT EXISTS feedback_rating INTEGER;

-- Leadership sub-metrics (for rating team leads)
ALTER TABLE peer_feedback ADD COLUMN IF NOT EXISTS org_guidance_rating INTEGER;
ALTER TABLE peer_feedback ADD COLUMN IF NOT EXISTS people_culture_rating INTEGER;
ALTER TABLE peer_feedback ADD COLUMN IF NOT EXISTS influence_rating INTEGER;

-- Add comments for documentation
COMMENT ON COLUMN peer_feedback.adaptability_rating IS 'Rating 1-5 for Adaptability (Behavioral pillar)';
COMMENT ON COLUMN peer_feedback.values_rating IS 'Rating 1-5 for Adherence to Company Values (Culture Fit pillar)';
COMMENT ON COLUMN peer_feedback.accountability_rating IS 'Rating 1-5 for Accountability & Ownership (Culture Fit pillar)';
COMMENT ON COLUMN peer_feedback.feedback_rating IS 'Rating 1-5 for Openness to Feedback (Growth pillar)';
COMMENT ON COLUMN peer_feedback.org_guidance_rating IS 'Rating 1-5 for Organizational Guidance (Leadership pillar - for TLs)';
COMMENT ON COLUMN peer_feedback.people_culture_rating IS 'Rating 1-5 for People & Culture Leadership (Leadership pillar - for TLs)';
COMMENT ON COLUMN peer_feedback.influence_rating IS 'Rating 1-5 for Leadership Influence (Leadership pillar - for TLs)';
