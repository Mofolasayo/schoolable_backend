ALTER TABLE team_quarterly_scores
    ADD COLUMN IF NOT EXISTS score_breakdown JSONB;
