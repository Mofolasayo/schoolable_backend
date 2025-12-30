-- V9: Supporting Tables for 5-Pillar Performance System
-- Creates tables for training, peer feedback, and other manual assessments

-- ============================================
-- 1. TRAINING & CERTIFICATIONS
-- ============================================
CREATE TABLE IF NOT EXISTS training_records (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Training details
    training_name VARCHAR(255) NOT NULL,
    training_type VARCHAR(50) CHECK (training_type IN ('course', 'certification', 'workshop', 'seminar', 'conference', 'self_study')),
    training_provider VARCHAR(255),
    
    -- Timing
    start_date DATE,
    completion_date DATE,
    duration_hours DECIMAL(5,1),
    
    -- Evidence
    certificate_url TEXT,
    certificate_number VARCHAR(100),
    
    -- Categorization
    skill_category VARCHAR(100), -- 'technical', 'leadership', 'soft_skills', etc.
    skill_tags TEXT[], -- Array of specific skills learned
    
    -- Impact
    cost DECIMAL(10,2),
    mandatory BOOLEAN DEFAULT false,
    
    -- Status
    status VARCHAR(20) DEFAULT 'completed' CHECK (status IN ('enrolled', 'in_progress', 'completed', 'failed', 'expired')),
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_training_employee ON training_records(employee_id);
CREATE INDEX idx_training_completion ON training_records(completion_date DESC);
CREATE INDEX idx_training_type ON training_records(training_type);
CREATE INDEX idx_training_status ON training_records(status);

-- ============================================
-- 2. PEER FEEDBACK SYSTEM
-- ============================================
CREATE TABLE IF NOT EXISTS peer_feedback_requests (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES profiles(id), -- Person being reviewed
    peer_id UUID NOT NULL REFERENCES profiles(id), -- Person giving feedback
    
    -- Request details
    requested_by UUID REFERENCES profiles(id), -- Usually manager or HR
    requested_at TIMESTAMPTZ DEFAULT NOW(),
    due_date DATE,
    
    -- Status
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'completed', 'declined', 'expired')),
    completed_at TIMESTAMPTZ,
    
    -- Feedback template used
    template_name VARCHAR(100),
    
    UNIQUE(review_id, peer_id)
);

CREATE TABLE IF NOT EXISTS peer_feedback_responses (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES peer_feedback_requests(id) ON DELETE CASCADE,
    
    -- Ratings (1-5 scale)
    teamwork_rating DECIMAL(2,1) CHECK (teamwork_rating >= 1 AND teamwork_rating <= 5),
    communication_rating DECIMAL(2,1) CHECK (communication_rating >= 1 AND communication_rating <= 5),
    attitude_rating DECIMAL(2,1) CHECK (attitude_rating >= 1 AND attitude_rating <= 5),
    reliability_rating DECIMAL(2,1) CHECK (reliability_rating >= 1 AND reliability_rating <= 5),
    collaboration_rating DECIMAL(2,1) CHECK (collaboration_rating >= 1 AND collaboration_rating <= 5),
    
    -- Open-ended feedback
    strengths TEXT,
    areas_for_improvement TEXT,
    additional_comments TEXT,
    
    -- Anonymous option
    is_anonymous BOOLEAN DEFAULT false,
    
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_peer_feedback_request ON peer_feedback_requests(review_id);
CREATE INDEX idx_peer_feedback_status ON peer_feedback_requests(status);

-- ============================================
-- 3. MANAGER ASSESSMENTS
-- ============================================
CREATE TABLE IF NOT EXISTS manager_assessments (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES profiles(id),
    manager_id UUID NOT NULL REFERENCES profiles(id),
    
    -- Behavioral Competence ratings
    adaptability_rating DECIMAL(2,1) CHECK (adaptability_rating >= 1 AND adaptability_rating <= 5),
    initiative_rating DECIMAL(2,1) CHECK (initiative_rating >= 1 AND initiative_rating <= 5),
    
    -- Culture Fit ratings
    company_values_rating DECIMAL(2,1) CHECK (company_values_rating >= 1 AND company_values_rating <= 5),
    work_ethics_rating DECIMAL(2,1) CHECK (work_ethics_rating >= 1 AND work_ethics_rating <= 5),
    
    -- Growth & Learning ratings
    skill_application_rating DECIMAL(2,1) CHECK (skill_application_rating >= 1 AND skill_application_rating <= 5),
    feedback_receptiveness_rating DECIMAL(2,1) CHECK (feedback_receptiveness_rating >= 1 AND feedback_receptiveness_rating <= 5),
    
    -- Leadership (only for team leads)
    decision_making_rating DECIMAL(2,1) CHECK (decision_making_rating >= 1 AND decision_making_rating <= 5),
    people_leadership_rating DECIMAL(2,1) CHECK (people_leadership_rating >= 1 AND people_leadership_rating <= 5),
    crisis_handling_rating DECIMAL(2,1) CHECK (crisis_handling_rating >= 1 AND crisis_handling_rating <= 5),
    
    -- Comments for each pillar
    behavioral_comments TEXT,
    culture_fit_comments TEXT,
    growth_comments TEXT,
    leadership_comments TEXT,
    
    -- Overall assessment
    overall_feedback TEXT,
    development_recommendations TEXT,
    
    -- Status
    status VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'submitted', 'reviewed')),
    submitted_at TIMESTAMPTZ,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(review_id, employee_id)
);

CREATE INDEX idx_manager_assessments_review ON manager_assessments(review_id);
CREATE INDEX idx_manager_assessments_manager ON manager_assessments(manager_id);

-- ============================================
-- 4. IMPROVEMENT SUGGESTIONS
-- ============================================
CREATE TABLE IF NOT EXISTS improvement_suggestions (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Suggestion details
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50), -- 'process', 'product', 'technology', 'culture', etc.
    expected_impact TEXT,
    
    -- Status workflow
    status VARCHAR(50) DEFAULT 'submitted' CHECK (status IN (
        'submitted', 'under_review', 'approved', 'in_progress', 
        'implemented', 'rejected', 'deferred'
    )),
    
    -- Impact assessment
    impact_level VARCHAR(20) CHECK (impact_level IN ('low', 'medium', 'high', 'critical')),
    cost_estimate DECIMAL(10,2),
    time_estimate_days INTEGER,
    
    -- Implementation
    implemented_date DATE,
    implementation_notes TEXT,
    actual_impact TEXT,
    
    -- Approval workflow
    reviewed_by UUID REFERENCES profiles(id),
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_improvement_employee ON improvement_suggestions(employee_id);
CREATE INDEX idx_improvement_status ON improvement_suggestions(status);
CREATE INDEX idx_improvement_impact ON improvement_suggestions(impact_level);

-- ============================================
-- 5. DISCIPLINARY ACTIONS (for Culture Fit tracking)
-- ============================================
CREATE TABLE IF NOT EXISTS disciplinary_actions (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    
    -- Incident details
    incident_date DATE NOT NULL,
    incident_type VARCHAR(50) CHECK (incident_type IN (
        'attendance', 'conduct', 'performance', 'policy_violation', 
        'safety', 'harassment', 'insubordination', 'other'
    )),
    severity VARCHAR(20) CHECK (severity IN ('minor', 'moderate', 'serious', 'critical')),
    description TEXT NOT NULL,
    
    -- Action taken
    action_type VARCHAR(50) CHECK (action_type IN (
        'verbal_warning', 'written_warning', 'suspension', 
        'final_warning', 'termination', 'counseling'
    )),
    action_description TEXT,
    
    -- Follow-up
    improvement_required TEXT,
    improvement_deadline DATE,
    follow_up_date DATE,
    resolution_status VARCHAR(20) DEFAULT 'open' CHECK (resolution_status IN ('open', 'in_progress', 'resolved', 'escalated')),
    
    -- Ownership
    reported_by UUID REFERENCES profiles(id),
    handled_by UUID REFERENCES profiles(id),
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_disciplinary_employee ON disciplinary_actions(employee_id);
CREATE INDEX idx_disciplinary_date ON disciplinary_actions(incident_date DESC);
CREATE INDEX idx_disciplinary_type ON disciplinary_actions(incident_type);

-- ============================================
-- 6. TEAM LEAD ASSESSMENTS (360-degree)
-- ============================================
CREATE TABLE IF NOT EXISTS team_lead_360_feedback (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    team_lead_id UUID NOT NULL REFERENCES profiles(id),
    assessor_id UUID NOT NULL REFERENCES profiles(id), -- Can be manager, peer, or direct report
    assessor_type VARCHAR(20) CHECK (assessor_type IN ('manager', 'peer', 'direct_report', 'self')),
    
    -- Leadership ratings
    organizational_guidance_rating DECIMAL(2,1) CHECK (organizational_guidance_rating >= 1 AND organizational_guidance_rating <= 5),
    people_leadership_rating DECIMAL(2,1) CHECK (people_leadership_rating >= 1 AND people_leadership_rating <= 5),
    decision_making_rating DECIMAL(2,1) CHECK (decision_making_rating >= 1 AND decision_making_rating <= 5),
    crisis_handling_rating DECIMAL(2,1) CHECK (crisis_handling_rating >= 1 AND crisis_handling_rating <= 5),
    leadership_influence_rating DECIMAL(2,1) CHECK (leadership_influence_rating >= 1 AND leadership_influence_rating <= 5),
    
    -- Comments
    leadership_strengths TEXT,
    leadership_development_areas TEXT,
    additional_comments TEXT,
    
    -- Status
    status VARCHAR(20) DEFAULT 'pending' CHECK (status IN ('pending', 'completed')),
    completed_at TIMESTAMPTZ,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(review_id, team_lead_id, assessor_id)
);

CREATE INDEX idx_360_review ON team_lead_360_feedback(review_id);
CREATE INDEX idx_360_team_lead ON team_lead_360_feedback(team_lead_id);

-- ============================================
-- 7. SELF ASSESSMENTS
-- ============================================
CREATE TABLE IF NOT EXISTS self_assessments (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES performance_reviews(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES profiles(id),
    
    -- Self-ratings for each pillar
    technical_self_rating DECIMAL(3,1),
    behavioral_self_rating DECIMAL(3,1),
    culture_fit_self_rating DECIMAL(3,1),
    growth_self_rating DECIMAL(3,1),
    collaboration_self_rating DECIMAL(3,1),
    
    -- Reflections
    accomplishments TEXT,
    challenges_faced TEXT,
    lessons_learned TEXT,
    goals_for_next_quarter TEXT,
    support_needed TEXT,
    
    -- Status
    status VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'submitted')),
    submitted_at TIMESTAMPTZ,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    UNIQUE(review_id, employee_id)
);

CREATE INDEX idx_self_assessment_review ON self_assessments(review_id);
CREATE INDEX idx_self_assessment_employee ON self_assessments(employee_id);

-- ============================================
-- 8. CALCULATED METRICS CACHE
-- ============================================
-- Store pre-calculated metrics to avoid repeated queries
CREATE TABLE IF NOT EXISTS performance_metrics_cache (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    quarter VARCHAR(10) NOT NULL,
    year INTEGER NOT NULL,
    
    -- Automated metric values
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(5,2),
    metric_details JSONB, -- Store breakdown/details
    
    -- Metadata
    calculated_at TIMESTAMPTZ DEFAULT NOW(),
    data_source VARCHAR(50), -- 'tasks', 'attendance', 'messages', etc.
    
    UNIQUE(employee_id, quarter, year, metric_name)
);

CREATE INDEX idx_metrics_cache_employee_quarter ON performance_metrics_cache(employee_id, quarter, year);
CREATE INDEX idx_metrics_cache_name ON performance_metrics_cache(metric_name);

-- ============================================
-- 9. UPDATE TRIGGERS
-- ============================================

CREATE TRIGGER update_training_records_updated_at
    BEFORE UPDATE ON training_records
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_manager_assessments_updated_at
    BEFORE UPDATE ON manager_assessments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_improvement_suggestions_updated_at
    BEFORE UPDATE ON improvement_suggestions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_disciplinary_actions_updated_at
    BEFORE UPDATE ON disciplinary_actions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_self_assessments_updated_at
    BEFORE UPDATE ON self_assessments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- 10. HELPER VIEWS
-- ============================================

-- View: Employee Training Summary
CREATE OR REPLACE VIEW v_employee_training_summary AS
SELECT 
    employee_id,
    DATE_TRUNC('quarter', completion_date) AS quarter,
    COUNT(*) AS trainings_completed,
    SUM(duration_hours) AS total_hours,
    ARRAY_AGG(DISTINCT skill_category) AS categories_covered
FROM training_records
WHERE status = 'completed'
GROUP BY employee_id, DATE_TRUNC('quarter', completion_date);

-- View: Peer Feedback Summary
CREATE OR REPLACE VIEW v_peer_feedback_summary AS
SELECT 
    pfr.employee_id,
    pr.quarter,
    pr.review_year,
    COUNT(pf.id) AS feedback_count,
    ROUND(AVG(pf.teamwork_rating), 2) AS avg_teamwork,
    ROUND(AVG(pf.communication_rating), 2) AS avg_communication,
    ROUND(AVG(pf.attitude_rating), 2) AS avg_attitude,
    ROUND(AVG(pf.reliability_rating), 2) AS avg_reliability,
    ROUND(AVG(pf.collaboration_rating), 2) AS avg_collaboration
FROM peer_feedback_requests pfr
JOIN peer_feedback_responses pf ON pfr.id = pf.request_id
JOIN performance_reviews pr ON pfr.review_id = pr.id
WHERE pfr.status = 'completed'
GROUP BY pfr.employee_id, pr.quarter, pr.review_year;

-- ============================================
-- MIGRATION COMPLETE
-- ============================================
-- Supporting tables for 5-Pillar System v1.0
-- Tables created: 8
-- Views created: 2
-- Triggers created: 5
