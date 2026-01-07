package com.schoolable.backend.performance;

import java.util.*;

/**
 * AURA SCORE SYSTEM V2 - Unified KPI Configuration
 * 
 * PILLAR WEIGHTS (same for all departments):
 * - Technical Competence: 35%
 * - Behavioral Competency: 25%
 * - Culture Fit: 20%
 * - Growth & Learning: 20%
 * 
 * Automation Level: ~80%
 * TL Rating Influence: ~19%
 * Peer Rating Influence: ~1%
 */
public class DepartmentKpiConfig {

    // ============================================================
    // UNIFIED PROFILE - Same weights for all departments
    // ============================================================

    public static final DepartmentProfile UNIFIED_PROFILE = new DepartmentProfile(
        "Standard",
        Map.of(
            // ================================================
            // TECHNICAL COMPETENCE (35% of Aura)
            // Primary: Daily Reports + Tasks + Individual KPIs
            // ================================================
            "technical", new PillarProfile(35, Map.of(
                // AI-graded daily report score
                "daily_report_score", new MetricConfig(35, "Daily Report Quality", "auto", "daily_reports",
                    "AI-graded score based on clarity, productivity, and KPI alignment", 100.0),
                
                // Combined task metrics
                "task_performance", new MetricConfig(30, "Task Performance", "auto", "tasks",
                    "Combined: completion rate + on-time delivery + quality rating", 100.0),
                
                // Individual KPI achievement (from IndividualKpi entity)
                "kpi_achievement", new MetricConfig(20, "Individual KPI Achievement", "auto", "individual_kpis",
                    "Weighted average of individual KPI achievement percentages", 100.0),
                
                // TL Technical Rating
                "tl_technical_rating", new MetricConfig(15, "Technical Rating", "team_lead", "weekly_report",
                    "Team Lead's weekly technical score (1-5 scale)", 5.0)
            )),

            // ================================================
            // BEHAVIORAL COMPETENCY (25% of Aura)
            // Primary: Attendance/Punctuality + Consistency
            // ================================================
            "behavioral", new PillarProfile(25, Map.of(
                // Merged attendance and punctuality with lateness penalties
                "attendance_punctuality", new MetricConfig(55, "Attendance & Punctuality", "auto", "attendance",
                    "Merged: On-time=100%, 1-15min late=95%, 16-30min=85%, 31-60min=70%", 95.0),
                
                // Work consistency (standard deviation of check-in times)
                "consistency", new MetricConfig(20, "Work Consistency", "auto", "attendance",
                    "Consistency of check-in times (low variance = high score)", 90.0),
                
                // TL Initiative Rating
                "initiative", new MetricConfig(15, "Initiative & Adaptability", "team_lead", "weekly_report",
                    "Average of TL initiative and adaptability scores", 5.0),
                
                // Peer Helpfulness Rating
                "peer_helpfulness", new MetricConfig(10, "Peer Helpfulness", "peer", "peer_feedback",
                    "Average peer helpfulness rating (requires 3+ ratings)", 5.0)
            )),

            // ================================================
            // CULTURE FIT (20% of Aura)
            // Primary: Compliance + TL/Peer Ratings
            // ================================================
            "culture_fit", new PillarProfile(20, Map.of(
                // Policy compliance
                "policy_compliance", new MetricConfig(40, "Policy Compliance", "auto", "compliance",
                    "Policies acknowledged and adhered to", 100.0),
                
                // TL Culture Rating
                "tl_culture_rating", new MetricConfig(30, "Culture Rating", "team_lead", "weekly_report",
                    "Team Lead's culture fit score (1-5 scale)", 5.0),
                
                // Peer Values Rating
                "peer_values", new MetricConfig(30, "Values Alignment", "peer", "peer_feedback",
                    "Peer ratings for values, accountability, feedback openness", 5.0)
            )),

            // ================================================
            // GROWTH & LEARNING (20% of Aura)
            // Primary: Training + Improvement Trend
            // ================================================
            "growth", new PillarProfile(20, Map.of(
                // Training participation (estimated hours)
                "training_participation", new MetricConfig(30, "Training Participation", "auto", "training",
                    "Estimated training hours from certificates (4 hrs each)", 8.0),
                
                // Certifications earned
                "certifications", new MetricConfig(25, "Certifications Earned", "auto", "training",
                    "New certificates uploaded this quarter", 1.0),
                
                // Improvement trend
                "improvement_trend", new MetricConfig(25, "Performance Improvement", "auto", "aura_history",
                    "Score improvement vs previous quarter", 5.0),
                
                // TL Growth Rating
                "tl_growth_rating", new MetricConfig(20, "Growth Rating", "team_lead", "weekly_report",
                    "Team Lead's growth/learning score (1-5 scale)", 5.0)
            ))
        )
    );

    // All departments use unified profile
    public static final Map<String, DepartmentProfile> DEPARTMENT_PROFILES = Map.of(
        "engineering", UNIFIED_PROFILE,
        "operations", UNIFIED_PROFILE,
        "sales", UNIFIED_PROFILE,
        "hr", UNIFIED_PROFILE,
        "finance", UNIFIED_PROFILE,
        "marketing", UNIFIED_PROFILE,
        "product", UNIFIED_PROFILE,
        "design", UNIFIED_PROFILE,
        "support", UNIFIED_PROFILE
    );

    public static final DepartmentProfile DEFAULT_PROFILE = UNIFIED_PROFILE;

    // ============================================================
    // HELPER CLASSES
    // ============================================================

    public static class DepartmentProfile {
        public final String displayName;
        public final Map<String, PillarProfile> pillars;

        public DepartmentProfile(String displayName, Map<String, PillarProfile> pillars) {
            this.displayName = displayName;
            this.pillars = pillars;
        }

        public int getAutoMetricCount() {
            int count = 0;
            for (PillarProfile pillar : pillars.values()) {
                for (MetricConfig metric : pillar.metrics.values()) {
                    if ("auto".equals(metric.source)) count++;
                }
            }
            return count;
        }

        public int getTotalMetricCount() {
            int count = 0;
            for (PillarProfile pillar : pillars.values()) {
                count += pillar.metrics.size();
            }
            return count;
        }

        public double getAutomationPercentage() {
            return (getAutoMetricCount() * 100.0) / getTotalMetricCount();
        }
    }

    public static class PillarProfile {
        public final int weight;
        public final Map<String, MetricConfig> metrics;

        public PillarProfile(int weight, Map<String, MetricConfig> metrics) {
            this.weight = weight;
            this.metrics = metrics;
        }
    }

    public static class MetricConfig {
        public final int weightInPillar;
        public final String displayName;
        public final String source; // "auto", "team_lead", "peer", "admin"
        public final String dataSource;
        public final String description;
        public final double target;

        public MetricConfig(int weight, String displayName, String source, String dataSource, 
                           String description, double target) {
            this.weightInPillar = weight;
            this.displayName = displayName;
            this.source = source;
            this.dataSource = dataSource;
            this.description = description;
            this.target = target;
        }
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    public static DepartmentProfile getProfileForDepartment(String department) {
        if (department == null) return DEFAULT_PROFILE;
        
        String normalizedDept = department.toLowerCase().trim();
        
        if (normalizedDept.contains("engineer") || normalizedDept.contains("develop") || 
            normalizedDept.contains("tech") || normalizedDept.contains("software")) {
            return DEPARTMENT_PROFILES.getOrDefault("engineering", DEFAULT_PROFILE);
        }
        if (normalizedDept.contains("operation") || normalizedDept.contains("admin")) {
            return DEPARTMENT_PROFILES.getOrDefault("operations", DEFAULT_PROFILE);
        }
        if (normalizedDept.contains("sale") || normalizedDept.contains("business")) {
            return DEPARTMENT_PROFILES.getOrDefault("sales", DEFAULT_PROFILE);
        }
        if (normalizedDept.contains("hr") || normalizedDept.contains("human") || normalizedDept.contains("people")) {
            return DEPARTMENT_PROFILES.getOrDefault("hr", DEFAULT_PROFILE);
        }
        if (normalizedDept.contains("finance") || normalizedDept.contains("account")) {
            return DEPARTMENT_PROFILES.getOrDefault("finance", DEFAULT_PROFILE);
        }
        if (normalizedDept.contains("market") || normalizedDept.contains("brand") || normalizedDept.contains("content")) {
            return DEPARTMENT_PROFILES.getOrDefault("marketing", DEFAULT_PROFILE);
        }
        
        return DEFAULT_PROFILE;
    }

    public static List<String> getAllDepartments() {
        return new ArrayList<>(DEPARTMENT_PROFILES.keySet());
    }

    public static Map<String, Integer> getPillarWeights() {
        return Map.of(
            "technical", 35,
            "behavioral", 25,
            "culture_fit", 20,
            "growth", 20
        );
    }
}
