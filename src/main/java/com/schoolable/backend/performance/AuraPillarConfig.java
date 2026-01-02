package com.schoolable.backend.performance;

import java.util.*;

/**
 * Constants and configuration for the Aura pillar system.
 * Defines all sub-metrics for each pillar and their weights.
 */
public final class AuraPillarConfig {

    private AuraPillarConfig() {} // Prevent instantiation

    // ==================== PILLAR NAMES ====================
    public static final String PILLAR_TECHNICAL = "technical";
    public static final String PILLAR_BEHAVIORAL = "behavioral";
    public static final String PILLAR_CULTURE_FIT = "culture_fit";
    public static final String PILLAR_GROWTH = "growth";
    public static final String PILLAR_LEADERSHIP = "leadership";

    // ==================== DATA SOURCES ====================
    public static final String SOURCE_AUTO = "auto";
    public static final String SOURCE_TEAM_LEAD = "team_lead";
    public static final String SOURCE_PEER_FEEDBACK = "peer_feedback";
    public static final String SOURCE_ADMIN = "admin";
    public static final String SOURCE_TEAM_FEEDBACK = "team_feedback"; // Subordinates rating TL

    // ==================== TECHNICAL - OPERATIONAL ROLES ====================
    public static final String TECH_OP_PROCESS_EXECUTION = "process_execution_accuracy";
    public static final String TECH_OP_DOCUMENTATION = "documentation_quality";
    public static final String TECH_OP_TASK_TIMELINESS = "task_completion_timeliness";
    public static final String TECH_OP_SOP_COMPLIANCE = "sop_compliance";
    public static final String TECH_OP_PROBLEM_SOLVING = "problem_solving";

    // ==================== TECHNICAL - LEADERSHIP ROLES ====================
    public static final String TECH_LEAD_STRATEGIC_VISION = "strategic_vision_execution";
    public static final String TECH_LEAD_BUSINESS_IMPACT = "business_growth_impact";
    public static final String TECH_LEAD_RESOURCE_ALLOCATION = "resource_allocation";
    public static final String TECH_LEAD_DECISION_QUALITY = "decision_quality";
    public static final String TECH_LEAD_RISK_MANAGEMENT = "risk_management";

    // ==================== BEHAVIORAL COMPETENCIES (Same for all) ====================
    public static final String BEHAV_TEAMWORK = "teamwork_collaboration";
    public static final String BEHAV_PROFESSIONALISM = "professionalism";
    public static final String BEHAV_TIME_MANAGEMENT = "time_management";
    public static final String BEHAV_ADAPTABILITY = "adaptability";
    public static final String BEHAV_INITIATIVE = "initiative";

    // ==================== CULTURE FIT (Same for all) ====================
    public static final String CULTURE_COMPANY_VALUES = "adherence_to_company_values";
    public static final String CULTURE_WORK_ETHICS = "work_ethics_integrity";
    public static final String CULTURE_ACCOUNTABILITY = "accountability_ownership";
    public static final String CULTURE_ATTITUDE = "attitude_towards_work_colleagues";
    public static final String CULTURE_RESPECT_POLICIES = "respect_organizational_norms_policies";

    // ==================== GROWTH & LEARNING (Same for all) ====================
    public static final String GROWTH_SKILL_DEV = "skill_development";
    public static final String GROWTH_TRAINING_PARTICIPATION = "participation_in_training";
    public static final String GROWTH_APPLY_SKILLS = "application_of_new_skills";
    public static final String GROWTH_CONTINUOUS_IMPROVEMENT = "continuous_improvement";
    public static final String GROWTH_OPENNESS_FEEDBACK = "openness_to_feedback";

    // ==================== LEADERSHIP PILLAR (Team Leads Only) ====================
    public static final String LEAD_ORG_GUIDANCE = "organizational_guidance";
    public static final String LEAD_PEOPLE_CULTURE = "people_culture_leadership";
    public static final String LEAD_EXEC_DECISION = "executive_decision_making";
    public static final String LEAD_CRISIS_HANDLING = "crisis_conflict_handling";
    public static final String LEAD_INFLUENCE = "leadership_influence";

    // ==================== SUB-METRIC CONFIGURATIONS ====================

    /**
     * Get all sub-metrics for Technical pillar (Operational roles)
     */
    public static List<SubMetricConfig> getTechnicalOperationalMetrics() {
        return List.of(
            new SubMetricConfig(TECH_OP_PROCESS_EXECUTION, "Process Execution Accuracy", 5.0, SOURCE_AUTO),
            new SubMetricConfig(TECH_OP_DOCUMENTATION, "Documentation Quality", 5.0, SOURCE_AUTO),
            new SubMetricConfig(TECH_OP_TASK_TIMELINESS, "Task Completion Timeliness", 5.0, SOURCE_AUTO),
            new SubMetricConfig(TECH_OP_SOP_COMPLIANCE, "SOP Compliance", 5.0, SOURCE_AUTO),
            new SubMetricConfig(TECH_OP_PROBLEM_SOLVING, "Problem-Solving", 5.0, SOURCE_TEAM_LEAD)
        );
    }

    /**
     * Get all sub-metrics for Technical pillar (Leadership roles)
     */
    public static List<SubMetricConfig> getTechnicalLeadershipMetrics() {
        return List.of(
            new SubMetricConfig(TECH_LEAD_STRATEGIC_VISION, "Strategic Vision & Execution", 5.0, SOURCE_ADMIN),
            new SubMetricConfig(TECH_LEAD_BUSINESS_IMPACT, "Business Growth Impact", 5.0, SOURCE_ADMIN),
            new SubMetricConfig(TECH_LEAD_RESOURCE_ALLOCATION, "Resource Allocation", 5.0, SOURCE_ADMIN),
            new SubMetricConfig(TECH_LEAD_DECISION_QUALITY, "Decision Quality", 5.0, SOURCE_ADMIN),
            new SubMetricConfig(TECH_LEAD_RISK_MANAGEMENT, "Risk Management", 5.0, SOURCE_ADMIN)
        );
    }

    /**
     * Get all sub-metrics for Behavioral pillar (All roles)
     */
    public static List<SubMetricConfig> getBehavioralMetrics() {
        return List.of(
            new SubMetricConfig(BEHAV_TEAMWORK, "Teamwork & Collaboration", 5.0, SOURCE_AUTO),
            new SubMetricConfig(BEHAV_PROFESSIONALISM, "Professionalism", 5.0, SOURCE_AUTO),
            new SubMetricConfig(BEHAV_TIME_MANAGEMENT, "Time Management", 5.0, SOURCE_AUTO),
            new SubMetricConfig(BEHAV_ADAPTABILITY, "Adaptability", 5.0, SOURCE_PEER_FEEDBACK),
            new SubMetricConfig(BEHAV_INITIATIVE, "Initiative", 5.0, SOURCE_TEAM_LEAD)
        );
    }

    /**
     * Get all sub-metrics for Culture Fit pillar (All roles)
     */
    public static List<SubMetricConfig> getCultureFitMetrics() {
        return List.of(
            new SubMetricConfig(CULTURE_COMPANY_VALUES, "Adherence to Company Values", 5.0, SOURCE_PEER_FEEDBACK),
            new SubMetricConfig(CULTURE_WORK_ETHICS, "Work Ethics & Integrity", 5.0, SOURCE_AUTO),
            new SubMetricConfig(CULTURE_ACCOUNTABILITY, "Accountability & Ownership", 5.0, SOURCE_PEER_FEEDBACK),
            new SubMetricConfig(CULTURE_ATTITUDE, "Attitude Towards Work & Colleagues", 5.0, SOURCE_TEAM_LEAD),
            new SubMetricConfig(CULTURE_RESPECT_POLICIES, "Respect for Organizational Norms & Policies", 5.0, SOURCE_AUTO)
        );
    }

    /**
     * Get all sub-metrics for Growth & Learning pillar (All roles)
     */
    public static List<SubMetricConfig> getGrowthMetrics() {
        return List.of(
            new SubMetricConfig(GROWTH_SKILL_DEV, "Skill Development", 5.0, SOURCE_AUTO),
            new SubMetricConfig(GROWTH_TRAINING_PARTICIPATION, "Participation in Training", 5.0, SOURCE_AUTO),
            new SubMetricConfig(GROWTH_APPLY_SKILLS, "Application of New Skills", 5.0, SOURCE_TEAM_LEAD),
            new SubMetricConfig(GROWTH_CONTINUOUS_IMPROVEMENT, "Continuous Improvement", 5.0, SOURCE_AUTO),
            new SubMetricConfig(GROWTH_OPENNESS_FEEDBACK, "Openness to Feedback", 5.0, SOURCE_PEER_FEEDBACK)
        );
    }

    /**
     * Get all sub-metrics for Leadership pillar (Team Leads only)
     */
    public static List<SubMetricConfig> getLeadershipMetrics() {
        return List.of(
            new SubMetricConfig(LEAD_ORG_GUIDANCE, "Organizational Guidance", 5.0, SOURCE_TEAM_FEEDBACK),
            new SubMetricConfig(LEAD_PEOPLE_CULTURE, "People & Culture Leadership", 5.0, SOURCE_TEAM_FEEDBACK),
            new SubMetricConfig(LEAD_EXEC_DECISION, "Executive Decision-Making", 5.0, SOURCE_ADMIN),
            new SubMetricConfig(LEAD_CRISIS_HANDLING, "Crisis/Conflict Handling", 5.0, SOURCE_ADMIN),
            new SubMetricConfig(LEAD_INFLUENCE, "Leadership Influence", 5.0, SOURCE_TEAM_FEEDBACK)
        );
    }

    /**
     * Get pillar weight based on role
     * Regular employees: 4 pillars × 25% = 100%
     * Team Leads: 5 pillars × 20% = 100%
     */
    public static double getPillarWeight(boolean isTeamLead) {
        return isTeamLead ? 20.0 : 25.0;
    }

    /**
     * Get sub-metric weight (within pillar)
     * Each pillar has 5 sub-metrics, so each is 20% of the pillar
     */
    public static double getSubMetricWeightWithinPillar() {
        return 20.0; // 5 sub-metrics × 20% = 100% of pillar
    }

    /**
     * Configuration holder for a sub-metric
     */
    public static class SubMetricConfig {
        private final String key;
        private final String displayName;
        private final double weight;
        private final String primarySource;

        public SubMetricConfig(String key, String displayName, double weight, String primarySource) {
            this.key = key;
            this.displayName = displayName;
            this.weight = weight;
            this.primarySource = primarySource;
        }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public double getWeight() { return weight; }
        public String getPrimarySource() { return primarySource; }
    }

    /**
     * Get display name for a sub-metric key
     */
    public static String getSubMetricDisplayName(String key) {
        Map<String, String> names = new HashMap<>();
        
        // Technical - Operational
        names.put(TECH_OP_PROCESS_EXECUTION, "Process Execution Accuracy");
        names.put(TECH_OP_DOCUMENTATION, "Documentation Quality");
        names.put(TECH_OP_TASK_TIMELINESS, "Task Completion Timeliness");
        names.put(TECH_OP_SOP_COMPLIANCE, "SOP Compliance");
        names.put(TECH_OP_PROBLEM_SOLVING, "Problem-Solving");
        
        // Technical - Leadership
        names.put(TECH_LEAD_STRATEGIC_VISION, "Strategic Vision & Execution");
        names.put(TECH_LEAD_BUSINESS_IMPACT, "Business Growth Impact");
        names.put(TECH_LEAD_RESOURCE_ALLOCATION, "Resource Allocation");
        names.put(TECH_LEAD_DECISION_QUALITY, "Decision Quality");
        names.put(TECH_LEAD_RISK_MANAGEMENT, "Risk Management");
        
        // Behavioral
        names.put(BEHAV_TEAMWORK, "Teamwork & Collaboration");
        names.put(BEHAV_PROFESSIONALISM, "Professionalism");
        names.put(BEHAV_TIME_MANAGEMENT, "Time Management");
        names.put(BEHAV_ADAPTABILITY, "Adaptability");
        names.put(BEHAV_INITIATIVE, "Initiative");
        
        // Culture Fit
        names.put(CULTURE_COMPANY_VALUES, "Adherence to Company Values");
        names.put(CULTURE_WORK_ETHICS, "Work Ethics & Integrity");
        names.put(CULTURE_ACCOUNTABILITY, "Accountability & Ownership");
        names.put(CULTURE_ATTITUDE, "Attitude Towards Work & Colleagues");
        names.put(CULTURE_RESPECT_POLICIES, "Respect for Organizational Norms & Policies");
        
        // Growth
        names.put(GROWTH_SKILL_DEV, "Skill Development");
        names.put(GROWTH_TRAINING_PARTICIPATION, "Participation in Training");
        names.put(GROWTH_APPLY_SKILLS, "Application of New Skills");
        names.put(GROWTH_CONTINUOUS_IMPROVEMENT, "Continuous Improvement");
        names.put(GROWTH_OPENNESS_FEEDBACK, "Openness to Feedback");
        
        // Leadership
        names.put(LEAD_ORG_GUIDANCE, "Organizational Guidance");
        names.put(LEAD_PEOPLE_CULTURE, "People & Culture Leadership");
        names.put(LEAD_EXEC_DECISION, "Executive Decision-Making");
        names.put(LEAD_CRISIS_HANDLING, "Crisis/Conflict Handling");
        names.put(LEAD_INFLUENCE, "Leadership Influence");
        
        return names.getOrDefault(key, key);
    }
}
