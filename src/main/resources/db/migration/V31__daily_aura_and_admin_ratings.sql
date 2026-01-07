-- V31: Daily Aura Snapshots and Admin Ratings for Team Leads
-- Enables daily Aura updates and Super Admin rating of Team Leads

-- ============================================
-- Daily Aura Snapshots Table
-- Stores daily calculated Aura scores for trend tracking
-- ============================================
CREATE TABLE IF NOT EXISTS daily_aura_snapshots (
    id SERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    
    -- Pillar scores
    technical_score DECIMAL(5,2),
    behavioral_score DECIMAL(5,2),
    culture_fit_score DECIMAL(5,2),
    growth_score DECIMAL(5,2),
    daily_aura DECIMAL(5,2),
    
    -- Daily activity tracking
    daily_report_submitted BOOLEAN DEFAULT FALSE,
    attendance_recorded BOOLEAN DEFAULT FALSE,
    tasks_completed INTEGER DEFAULT 0,
    
    -- Change tracking
    aura_change DECIMAL(5,2) DEFAULT 0, -- Change from previous day
    
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(employee_id, snapshot_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_aura_employee ON daily_aura_snapshots(employee_id);
CREATE INDEX IF NOT EXISTS idx_daily_aura_date ON daily_aura_snapshots(snapshot_date);
CREATE INDEX IF NOT EXISTS idx_daily_aura_employee_date ON daily_aura_snapshots(employee_id, snapshot_date DESC);

-- ============================================
-- Admin Ratings for Team Leads
-- Super Admin rates Team Leads (similar to TL rating staff)
-- ============================================
CREATE TABLE IF NOT EXISTS admin_team_lead_ratings (
    id SERIAL PRIMARY KEY,
    team_lead_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    rated_by_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Period
    week_number INTEGER NOT NULL,
    year INTEGER NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    
    -- Pillar Scores (1-5 scale)
    leadership_score INTEGER CHECK (leadership_score >= 1 AND leadership_score <= 5),
    team_management_score INTEGER CHECK (team_management_score >= 1 AND team_management_score <= 5),
    communication_score INTEGER CHECK (communication_score >= 1 AND communication_score <= 5),
    results_delivery_score INTEGER CHECK (results_delivery_score >= 1 AND results_delivery_score <= 5),
    culture_champion_score INTEGER CHECK (culture_champion_score >= 1 AND culture_champion_score <= 5),
    
    -- Notes
    leadership_notes TEXT,
    areas_of_strength TEXT,
    areas_for_improvement TEXT,
    
    -- Status
    status VARCHAR(20) DEFAULT 'submitted',
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(team_lead_id, week_number, year)
);

CREATE INDEX IF NOT EXISTS idx_admin_ratings_tl ON admin_team_lead_ratings(team_lead_id);
CREATE INDEX IF NOT EXISTS idx_admin_ratings_week ON admin_team_lead_ratings(year, week_number);

-- ============================================
-- Aura Trend Alerts Table
-- Stores alerts for significant Aura changes
-- ============================================
CREATE TABLE IF NOT EXISTS aura_trend_alerts (
    id SERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL, -- 'SCORE_DROP', 'SCORE_INCREASE', 'CONSISTENT_DECLINE', 'CONSISTENT_IMPROVEMENT', 'BELOW_THRESHOLD'
    
    -- Alert details
    previous_score DECIMAL(5,2),
    current_score DECIMAL(5,2),
    change_percentage DECIMAL(5,2),
    weeks_trending INTEGER DEFAULT 1, -- For consistent trends
    
    -- Status
    is_read BOOLEAN DEFAULT FALSE,
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by UUID REFERENCES profiles(id),
    acknowledged_at TIMESTAMP,
    
    -- Additional context
    alert_message TEXT,
    related_pillar VARCHAR(50), -- Which pillar caused the change
    
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_aura_alerts_employee ON aura_trend_alerts(employee_id);
CREATE INDEX IF NOT EXISTS idx_aura_alerts_unread ON aura_trend_alerts(employee_id, is_read) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_aura_alerts_type ON aura_trend_alerts(alert_type);

-- ============================================
-- Comments
-- ============================================
COMMENT ON TABLE daily_aura_snapshots IS 'Daily calculated Aura scores for trend tracking and real-time feedback';
COMMENT ON TABLE admin_team_lead_ratings IS 'Super Admin ratings for Team Leads - feeds into TL Aura scores';
COMMENT ON TABLE aura_trend_alerts IS 'Alerts for significant Aura score changes requiring attention';
