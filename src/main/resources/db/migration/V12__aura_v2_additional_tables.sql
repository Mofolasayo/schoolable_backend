-- V12: Additional Tables for Aura V2 System
-- Creates tables needed for auto-calculated performance metrics

-- ============================================
-- 1. TRAINING RECORDS TABLE
-- ============================================
-- Tracks employee training completions for Growth & Learning pillar

CREATE TABLE IF NOT EXISTS training_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Training details
    training_name VARCHAR(255) NOT NULL,
    training_type VARCHAR(50) NOT NULL, -- 'online', 'in-person', 'workshop', 'certification'
    provider VARCHAR(255), -- Internal, External provider name
    
    -- Category for skill matching
    skill_category VARCHAR(100), -- 'technical', 'soft_skills', 'leadership', 'compliance', 'product'
    
    -- Duration
    duration_hours DECIMAL(5,2),
    
    -- Status tracking
    status VARCHAR(20) DEFAULT 'in_progress', -- 'in_progress', 'completed', 'failed', 'expired'
    started_at TIMESTAMPTZ,
    completion_date DATE,
    expiry_date DATE, -- For certifications that expire
    
    -- Score/Certificate
    score DECIMAL(5,2), -- Percentage score if applicable
    certificate_url TEXT,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- Unique constraint: one record per employee per training
    UNIQUE(employee_id, training_name, started_at)
);

-- Indexes for training_records
CREATE INDEX IF NOT EXISTS idx_training_employee ON training_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_training_status ON training_records(status);
CREATE INDEX IF NOT EXISTS idx_training_completion ON training_records(completion_date);
CREATE INDEX IF NOT EXISTS idx_training_category ON training_records(skill_category);

-- ============================================
-- 2. PEER FEEDBACK TABLE
-- ============================================
-- Stores peer-to-peer ratings for Collaboration pillar

CREATE TABLE IF NOT EXISTS peer_feedback (
    id BIGSERIAL PRIMARY KEY,
    
    -- Who is giving and receiving feedback
    from_employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    to_employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Time period
    quarter VARCHAR(10) NOT NULL, -- 'Q1', 'Q2', 'Q3', 'Q4'
    year INTEGER NOT NULL,
    
    -- Rating (1-5 scale)
    support_rating INTEGER NOT NULL CHECK (support_rating >= 1 AND support_rating <= 5),
    -- "How helpful was this person when you needed assistance?"
    
    collaboration_rating INTEGER CHECK (collaboration_rating >= 1 AND collaboration_rating <= 5),
    -- "How well did this person work with you on shared tasks?"
    
    communication_rating INTEGER CHECK (communication_rating >= 1 AND communication_rating <= 5),
    -- "How effectively did this person communicate with you?"
    
    -- Optional comments
    strengths TEXT,
    areas_for_improvement TEXT,
    
    -- Metadata
    is_anonymous BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'submitted', -- 'draft', 'submitted'
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- One feedback per peer pair per quarter
    UNIQUE(from_employee_id, to_employee_id, quarter, year)
);

-- Indexes for peer_feedback
CREATE INDEX IF NOT EXISTS idx_peer_feedback_to ON peer_feedback(to_employee_id);
CREATE INDEX IF NOT EXISTS idx_peer_feedback_from ON peer_feedback(from_employee_id);
CREATE INDEX IF NOT EXISTS idx_peer_feedback_quarter ON peer_feedback(quarter, year);

-- ============================================
-- 3. ANNOUNCEMENT VIEWS TABLE
-- ============================================
-- Tracks who viewed which announcements for Engagement metric

CREATE TABLE IF NOT EXISTS announcement_views (
    id BIGSERIAL PRIMARY KEY,
    announcement_id UUID NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- View tracking
    viewed_at TIMESTAMPTZ DEFAULT NOW(),
    acknowledged BOOLEAN DEFAULT FALSE, -- Did they click "acknowledge"?
    acknowledged_at TIMESTAMPTZ,
    
    -- One view record per employee per announcement
    UNIQUE(announcement_id, employee_id)
);

-- Indexes for announcement_views
CREATE INDEX IF NOT EXISTS idx_announcement_views_employee ON announcement_views(employee_id);
CREATE INDEX IF NOT EXISTS idx_announcement_views_announcement ON announcement_views(announcement_id);
CREATE INDEX IF NOT EXISTS idx_announcement_views_date ON announcement_views(viewed_at);

-- ============================================
-- 4. IMPROVEMENT SUGGESTIONS TABLE
-- ============================================
-- Tracks improvement ideas submitted by employees (Growth pillar)

CREATE TABLE IF NOT EXISTS improvement_suggestions (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Suggestion details
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50), -- 'process', 'product', 'culture', 'technology', 'other'
    
    -- Impact assessment
    estimated_impact VARCHAR(20), -- 'low', 'medium', 'high'
    
    -- Status tracking
    status VARCHAR(20) DEFAULT 'submitted', -- 'submitted', 'under_review', 'approved', 'implemented', 'rejected'
    reviewed_by UUID REFERENCES profiles(id),
    reviewed_at TIMESTAMPTZ,
    implementation_notes TEXT,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for improvement_suggestions
CREATE INDEX IF NOT EXISTS idx_suggestions_employee ON improvement_suggestions(employee_id);
CREATE INDEX IF NOT EXISTS idx_suggestions_status ON improvement_suggestions(status);
CREATE INDEX IF NOT EXISTS idx_suggestions_date ON improvement_suggestions(created_at);

-- ============================================
-- 5. DISCIPLINARY ACTIONS TABLE
-- ============================================
-- Tracks policy violations for Culture Fit/Ethics

CREATE TABLE IF NOT EXISTS disciplinary_actions (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Action details
    action_type VARCHAR(50) NOT NULL, -- 'verbal_warning', 'written_warning', 'suspension', 'termination'
    reason VARCHAR(255) NOT NULL,
    description TEXT,
    
    -- Severity (affects score deduction)
    severity INTEGER DEFAULT 1 CHECK (severity >= 1 AND severity <= 5), -- 1=minor, 5=severe
    
    -- Date tracking
    incident_date DATE NOT NULL,
    action_date DATE NOT NULL,
    expiry_date DATE, -- When warning expires from record
    
    -- Who issued
    issued_by UUID REFERENCES profiles(id),
    
    -- Status
    status VARCHAR(20) DEFAULT 'active', -- 'active', 'expired', 'appealed', 'overturned'
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for disciplinary_actions
CREATE INDEX IF NOT EXISTS idx_disciplinary_employee ON disciplinary_actions(employee_id);
CREATE INDEX IF NOT EXISTS idx_disciplinary_status ON disciplinary_actions(status);
CREATE INDEX IF NOT EXISTS idx_disciplinary_date ON disciplinary_actions(action_date);

-- ============================================
-- 6. TASK ATTACHMENTS TABLE (if not exists)
-- ============================================
-- Tracks documentation created by employees

CREATE TABLE IF NOT EXISTS task_attachments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    
    -- Who created
    created_by UUID NOT NULL REFERENCES profiles(id),
    
    -- File details
    file_name VARCHAR(255) NOT NULL,
    file_url TEXT NOT NULL,
    file_type VARCHAR(100), -- 'pdf', 'doc', 'image', 'spreadsheet', etc.
    file_size_bytes BIGINT,
    
    -- Metadata
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for task_attachments
CREATE INDEX IF NOT EXISTS idx_task_attachments_task ON task_attachments(task_id);
CREATE INDEX IF NOT EXISTS idx_task_attachments_creator ON task_attachments(created_by);

-- ============================================
-- 7. VIEWS FOR PERFORMANCE CALCULATIONS
-- ============================================

-- Peer feedback summary per employee
CREATE OR REPLACE VIEW v_peer_feedback_summary AS
SELECT 
    to_employee_id AS employee_id,
    quarter,
    year,
    COUNT(*) AS feedback_count,
    ROUND(AVG(support_rating), 2) AS avg_support_rating,
    ROUND(AVG(COALESCE(collaboration_rating, support_rating)), 2) AS avg_collaboration_rating,
    ROUND(AVG(COALESCE(communication_rating, support_rating)), 2) AS avg_communication_rating,
    ROUND(
        (AVG(support_rating) + 
         AVG(COALESCE(collaboration_rating, support_rating)) + 
         AVG(COALESCE(communication_rating, support_rating))
        ) / 3 * 20, 2
    ) AS peer_score_pct
FROM peer_feedback
WHERE status = 'submitted'
GROUP BY to_employee_id, quarter, year;

-- Training summary per employee per quarter
CREATE OR REPLACE VIEW v_training_summary AS
SELECT 
    employee_id,
    CASE 
        WHEN EXTRACT(MONTH FROM completion_date) BETWEEN 1 AND 3 THEN 'Q1'
        WHEN EXTRACT(MONTH FROM completion_date) BETWEEN 4 AND 6 THEN 'Q2'
        WHEN EXTRACT(MONTH FROM completion_date) BETWEEN 7 AND 9 THEN 'Q3'
        ELSE 'Q4'
    END AS quarter,
    EXTRACT(YEAR FROM completion_date) AS year,
    COUNT(*) AS trainings_completed,
    SUM(duration_hours) AS total_hours,
    CASE 
        WHEN COUNT(*) >= 5 THEN 100
        WHEN COUNT(*) >= 3 THEN 80
        WHEN COUNT(*) >= 1 THEN 60
        ELSE 40
    END AS training_score
FROM training_records
WHERE status = 'completed'
GROUP BY employee_id, 
    CASE 
        WHEN EXTRACT(MONTH FROM completion_date) BETWEEN 1 AND 3 THEN 'Q1'
        WHEN EXTRACT(MONTH FROM completion_date) BETWEEN 4 AND 6 THEN 'Q2'
        WHEN EXTRACT(MONTH FROM completion_date) BETWEEN 7 AND 9 THEN 'Q3'
        ELSE 'Q4'
    END,
    EXTRACT(YEAR FROM completion_date);

-- Announcement engagement per employee
CREATE OR REPLACE VIEW v_announcement_engagement AS
SELECT 
    av.employee_id,
    DATE_TRUNC('quarter', av.viewed_at) AS quarter_start,
    COUNT(DISTINCT av.announcement_id) AS announcements_viewed,
    COUNT(CASE WHEN av.acknowledged THEN 1 END) AS announcements_acknowledged,
    COUNT(DISTINCT a.id) AS total_announcements,
    ROUND(
        COUNT(DISTINCT av.announcement_id)::NUMERIC / 
        NULLIF(COUNT(DISTINCT a.id), 0) * 100, 2
    ) AS engagement_rate
FROM announcement_views av
JOIN announcements a ON av.announcement_id = a.id
GROUP BY av.employee_id, DATE_TRUNC('quarter', av.viewed_at);

-- ============================================
-- COMMENTS
-- ============================================

COMMENT ON TABLE training_records IS 'Tracks employee training completions for Growth & Learning pillar';
COMMENT ON TABLE peer_feedback IS 'Stores peer-to-peer ratings for Collaboration pillar';
COMMENT ON TABLE announcement_views IS 'Tracks announcement views for Engagement metric';
COMMENT ON TABLE improvement_suggestions IS 'Tracks employee improvement ideas for Continuous Improvement metric';
COMMENT ON TABLE disciplinary_actions IS 'Tracks policy violations for Ethics/Compliance metrics';
COMMENT ON TABLE task_attachments IS 'Tracks documentation created for Knowledge Sharing metric';

-- ============================================
-- MIGRATION COMPLETE
-- ============================================
-- Tables created: 6
-- Views created: 3
