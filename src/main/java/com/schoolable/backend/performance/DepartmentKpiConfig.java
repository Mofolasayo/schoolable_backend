package com.schoolable.backend.performance;

import java.util.*;

/**
 * Unified KPI configuration for Aura Score calculation.
 * 
 * STANDARDIZED PILLAR WEIGHTS (same for all departments):
 * - Technical Competence: 40% (KPI/Daily Report AI Score is primary)
 * - Behavioral Competency: 25%
 * - Culture Fit: 25%
 * - Growth & Learning: 10%
 * 
 * Technical Competence is driven primarily by:
 * - Daily Report AI Score (50% of pillar) - AI grades reports against defined KPIs
 * - Task metrics (50% of pillar) - Task completion, on-time delivery, quality
 */
public class DepartmentKpiConfig {

    // ============================================================
    // UNIFIED PROFILE - Same weights for all departments
    // ============================================================

    /**
     * UNIFIED AURA PROFILE
     * All departments use the same pillar weights:
     * - Technical Competence: 40%
     * - Behavioral Competency: 25%
     * - Culture Fit: 25%
     * - Growth & Learning: 10%
     */
    public static final DepartmentProfile UNIFIED_PROFILE = new DepartmentProfile(
        "Standard",
        Map.of(
            // ================================================
            // TECHNICAL COMPETENCE (40% of Aura)
            // Primary driver: Daily Report AI Score (50%)
            // Secondary: Task metrics (50%)
            // ================================================
            "technical", new PillarProfile(40, Map.of(
                // PRIMARY: Daily Report AI Score - AI grades reports against KPIs
                "daily_report_score", new MetricConfig(50, "Daily Report & KPI Performance", "auto", "daily_reports",
                    "AI-graded daily report score based on tasks completed, productivity, and KPI alignment", 100.0),
                
                // SECONDARY: Task-based metrics
                "task_completion_rate", new MetricConfig(20, "Task Completion Rate", "auto", "tasks",
                    "Percentage of assigned tasks completed", 100.0),
                "on_time_delivery", new MetricConfig(15, "On-Time Delivery", "auto", "tasks",
                    "Tasks completed before or on due date", 95.0),
                "task_quality", new MetricConfig(15, "Task Quality", "auto", "tasks",
                    "Quality rating from task creators (no reopens)", 90.0)
            )),

            // ================================================
            // BEHAVIORAL COMPETENCY (25% of Aura)
            // Attendance with lateness penalties, consistency, professionalism
            // ================================================
            "behavioral", new PillarProfile(25, Map.of(
                "attendance_punctuality", new MetricConfig(55, "Attendance & Punctuality", "auto", "attendance",
                    "Presence with lateness penalties: On-time=100%, 1-15min late=95%, 16-30min=85%, 31-60min=70%", 95.0),
                "consistency", new MetricConfig(20, "Work Consistency", "auto", "attendance",
                    "Consistent check-in pattern and work schedule", 90.0),
                "initiative", new MetricConfig(25, "Initiative & Professionalism", "team_lead", "weekly_report",
                    "Proactive behavior and professional conduct (Team Lead rated)", 4.0)
            )),

            // ================================================
            // CULTURE FIT (25% of Aura)
            // Compliance, collaboration, attitude
            // ================================================
            "culture_fit", new PillarProfile(25, Map.of(
                "policy_compliance", new MetricConfig(30, "Policy Compliance", "auto", "compliance",
                    "SOP/Policy acknowledgments and adherence", 100.0),
                "training_compliance", new MetricConfig(25, "Training Compliance", "auto", "training",
                    "Required trainings completed", 100.0),
                "team_collaboration", new MetricConfig(25, "Team Collaboration", "auto", "tasks",
                    "Cross-team support and collaboration", 100.0),
                "attitude", new MetricConfig(20, "Attitude & Values", "team_lead", "weekly_report",
                    "Positive attitude and alignment with company values (Team Lead rated)", 4.0)
            )),

            // ================================================
            // GROWTH & LEARNING (10% of Aura)
            // Training, certifications, improvement
            // ================================================
            "growth", new PillarProfile(10, Map.of(
                "training_hours", new MetricConfig(35, "Training Participation", "auto", "training",
                    "Quarterly training hours completed", 8.0),
                "certifications", new MetricConfig(35, "Certifications Earned", "auto", "training",
                    "New certificates uploaded this quarter", 1.0),
                "improvement_trend", new MetricConfig(30, "Performance Improvement", "auto", "aura",
                    "Score improvement vs last quarter", 5.0)
            ))
        )
    );

    // ============================================================
    // DEPARTMENT PROFILES (all use UNIFIED weights now)
    // Department-specific customization is minimal
    // ============================================================

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

    // Default profile for departments not explicitly configured
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
        public final int weight; // Weight of this pillar in overall score
        public final Map<String, MetricConfig> metrics;

        public PillarProfile(int weight, Map<String, MetricConfig> metrics) {
            this.weight = weight;
            this.metrics = metrics;
        }
    }

    public static class MetricConfig {
        public final int weightInPillar; // Weight within the pillar (should sum to 100)
        public final String displayName;
        public final String source; // "auto" or "team_lead"
        public final String dataSource; // tasks, attendance, compliance, training, weekly_report, daily_reports, etc.
        public final String description;
        public final double target; // What value = 100% score

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
        
        // All departments now use the same unified profile
        // This mapping is kept for future department-specific customization if needed
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
        if (normalizedDept.contains("product")) {
            return DEPARTMENT_PROFILES.getOrDefault("product", DEFAULT_PROFILE);
        }
        if (normalizedDept.contains("design") || normalizedDept.contains("creative")) {
            return DEPARTMENT_PROFILES.getOrDefault("design", DEFAULT_PROFILE);
        }
        if (normalizedDept.contains("support") || normalizedDept.contains("customer")) {
            return DEPARTMENT_PROFILES.getOrDefault("support", DEFAULT_PROFILE);
        }
        
        return DEFAULT_PROFILE;
    }

    public static List<String> getAllDepartments() {
        return new ArrayList<>(DEPARTMENT_PROFILES.keySet());
    }

    /**
     * Get pillar weights - useful for UI display
     */
    public static Map<String, Integer> getPillarWeights() {
        return Map.of(
            "technical", 40,
            "behavioral", 25,
            "culture_fit", 25,
            "growth", 10
        );
    }
}
