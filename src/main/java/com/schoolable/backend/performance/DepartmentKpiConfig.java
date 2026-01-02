package com.schoolable.backend.performance;

import java.util.*;

/**
 * Department-specific KPI configuration.
 * Each department can have custom metrics, weights, and targets.
 * This replaces the complex peer-rating system with fully automated calculations.
 */
public class DepartmentKpiConfig {

    // ============================================================
    // DEPARTMENT KPI PROFILES
    // Each department defines which metrics matter and their weights
    // ============================================================

    public static final Map<String, DepartmentProfile> DEPARTMENT_PROFILES = Map.of(
        // Engineering / Development
        "engineering", new DepartmentProfile(
            "Engineering",
            Map.of(
                // Technical Pillar (30%) - Heavy focus on delivery
                "technical", new PillarProfile(30, Map.of(
                    "task_completion_rate", new MetricConfig(25, "Task Completion Rate", "auto", "tasks", 
                        "Percentage of assigned tasks completed", 100.0),
                    "on_time_delivery", new MetricConfig(25, "On-Time Delivery", "auto", "tasks",
                        "Tasks completed before or on due date", 95.0),
                    "task_quality", new MetricConfig(20, "Task Quality (No Reopens)", "auto", "tasks",
                        "Tasks completed without being reopened", 90.0),
                    "documentation", new MetricConfig(15, "Documentation Quality", "auto", "tasks",
                        "Tasks with proper attachments/notes", 80.0),
                    "workload_handling", new MetricConfig(15, "Workload Management", "auto", "tasks",
                        "Ability to handle multiple tasks", 100.0)
                )),
                // Behavioral Pillar (25%) - Professionalism
                "behavioral", new PillarProfile(25, Map.of(
                    "attendance_rate", new MetricConfig(30, "Attendance Rate", "auto", "attendance",
                        "Days present vs expected", 95.0),
                    "punctuality", new MetricConfig(25, "Punctuality", "auto", "attendance",
                        "On-time check-ins", 90.0),
                    "consistency", new MetricConfig(20, "Work Consistency", "auto", "attendance",
                        "Consistent attendance pattern", 90.0),
                    "initiative", new MetricConfig(25, "Initiative", "team_lead", "weekly_report",
                        "Proactive behavior (TL rated)", 4.0)
                )),
                // Culture Fit (20%)
                "culture_fit", new PillarProfile(20, Map.of(
                    "policy_compliance", new MetricConfig(35, "Policy Compliance", "auto", "compliance",
                        "SOP/Policy acknowledgments", 100.0),
                    "training_compliance", new MetricConfig(30, "Training Compliance", "auto", "training",
                        "Required trainings completed", 100.0),
                    "zero_violations", new MetricConfig(20, "Zero HR Violations", "auto", "hr",
                        "No disciplinary incidents", 0.0),
                    "attitude", new MetricConfig(15, "Attitude", "team_lead", "weekly_report",
                        "Positive attitude (TL rated)", 4.0)
                )),
                // Growth & Learning (25%) - Continuous improvement
                "growth", new PillarProfile(25, Map.of(
                    "training_hours", new MetricConfig(25, "Training Participation", "auto", "training",
                        "Quarterly training hours", 8.0),
                    "certifications", new MetricConfig(25, "Certifications Earned", "auto", "training",
                        "New certificates this quarter", 1.0),
                    "improvement_trend", new MetricConfig(25, "Score Improvement", "auto", "aura",
                        "Improvement vs last quarter", 5.0),
                    "skill_application", new MetricConfig(25, "Skills Applied", "team_lead", "weekly_report",
                        "New skills used in work (TL)", 4.0)
                ))
            )
        ),

        // Operations / Admin
        "operations", new DepartmentProfile(
            "Operations",
            Map.of(
                "technical", new PillarProfile(25, Map.of(
                    "task_completion_rate", new MetricConfig(30, "Task Completion", "auto", "tasks", "", 100.0),
                    "process_adherence", new MetricConfig(25, "Process Adherence", "auto", "compliance", "", 100.0),
                    "on_time_delivery", new MetricConfig(25, "On-Time Delivery", "auto", "tasks", "", 95.0),
                    "accuracy", new MetricConfig(20, "Work Accuracy", "team_lead", "weekly_report", "", 4.0)
                )),
                "behavioral", new PillarProfile(30, Map.of( // Higher weight for ops
                    "attendance_rate", new MetricConfig(35, "Attendance", "auto", "attendance", "", 98.0),
                    "punctuality", new MetricConfig(30, "Punctuality", "auto", "attendance", "", 95.0),
                    "reliability", new MetricConfig(20, "Reliability", "team_lead", "weekly_report", "", 4.0),
                    "initiative", new MetricConfig(15, "Initiative", "team_lead", "weekly_report", "", 4.0)
                )),
                "culture_fit", new PillarProfile(25, Map.of(
                    "policy_compliance", new MetricConfig(40, "Policy Compliance", "auto", "compliance", "", 100.0),
                    "team_collaboration", new MetricConfig(30, "Collaboration", "auto", "tasks", "", 100.0),
                    "attitude", new MetricConfig(30, "Attitude", "team_lead", "weekly_report", "", 4.0)
                )),
                "growth", new PillarProfile(20, Map.of(
                    "training_completion", new MetricConfig(40, "Training Completion", "auto", "training", "", 100.0),
                    "improvement_trend", new MetricConfig(30, "Improvement", "auto", "aura", "", 5.0),
                    "adaptability", new MetricConfig(30, "Adaptability", "team_lead", "weekly_report", "", 4.0)
                ))
            )
        ),

        // Sales / Business Development
        "sales", new DepartmentProfile(
            "Sales",
            Map.of(
                "technical", new PillarProfile(35, Map.of( // Higher - results matter
                    "target_achievement", new MetricConfig(40, "Sales Target %", "auto", "sales_data", "", 100.0),
                    "deals_closed", new MetricConfig(25, "Deals Closed", "auto", "sales_data", "", 10.0),
                    "pipeline_growth", new MetricConfig(20, "Pipeline Growth", "auto", "sales_data", "", 20.0),
                    "client_meetings", new MetricConfig(15, "Client Meetings", "auto", "calendar", "", 15.0)
                )),
                "behavioral", new PillarProfile(25, Map.of(
                    "attendance_rate", new MetricConfig(25, "Attendance", "auto", "attendance", "", 90.0),
                    "client_responsiveness", new MetricConfig(35, "Response Time", "auto", "communications", "", 2.0),
                    "professionalism", new MetricConfig(20, "Professionalism", "team_lead", "weekly_report", "", 4.0),
                    "initiative", new MetricConfig(20, "Initiative", "team_lead", "weekly_report", "", 4.0)
                )),
                "culture_fit", new PillarProfile(20, Map.of(
                    "team_support", new MetricConfig(40, "Team Support", "auto", "tasks", "", 100.0),
                    "policy_compliance", new MetricConfig(30, "Compliance", "auto", "compliance", "", 100.0),
                    "attitude", new MetricConfig(30, "Attitude", "team_lead", "weekly_report", "", 4.0)
                )),
                "growth", new PillarProfile(20, Map.of(
                    "product_knowledge", new MetricConfig(30, "Product Training", "auto", "training", "", 100.0),
                    "skill_development", new MetricConfig(35, "Sales Skills", "auto", "training", "", 2.0),
                    "improvement_trend", new MetricConfig(35, "Performance Trend", "auto", "aura", "", 10.0)
                ))
            )
        ),

        // HR / People Operations
        "hr", new DepartmentProfile(
            "Human Resources",
            Map.of(
                "technical", new PillarProfile(25, Map.of(
                    "task_completion", new MetricConfig(30, "Task Completion", "auto", "tasks", "", 100.0),
                    "process_adherence", new MetricConfig(30, "Process Adherence", "auto", "compliance", "", 100.0),
                    "on_time_delivery", new MetricConfig(25, "Timeliness", "auto", "tasks", "", 95.0),
                    "documentation", new MetricConfig(15, "Documentation", "auto", "tasks", "", 90.0)
                )),
                "behavioral", new PillarProfile(30, Map.of(
                    "attendance_rate", new MetricConfig(25, "Attendance", "auto", "attendance", "", 98.0),
                    "punctuality", new MetricConfig(20, "Punctuality", "auto", "attendance", "", 95.0),
                    "confidentiality", new MetricConfig(30, "Confidentiality", "team_lead", "weekly_report", "", 5.0),
                    "professionalism", new MetricConfig(25, "Professionalism", "team_lead", "weekly_report", "", 4.0)
                )),
                "culture_fit", new PillarProfile(25, Map.of(
                    "policy_compliance", new MetricConfig(35, "Policy Compliance", "auto", "compliance", "", 100.0),
                    "employee_support", new MetricConfig(35, "Employee Support", "auto", "tasks", "", 100.0),
                    "culture_champion", new MetricConfig(30, "Culture Champion", "team_lead", "weekly_report", "", 4.0)
                )),
                "growth", new PillarProfile(20, Map.of(
                    "hr_certifications", new MetricConfig(35, "HR Certifications", "auto", "training", "", 1.0),
                    "training_completion", new MetricConfig(35, "Training", "auto", "training", "", 100.0),
                    "improvement_trend", new MetricConfig(30, "Improvement", "auto", "aura", "", 5.0)
                ))
            )
        ),

        // Finance / Accounting
        "finance", new DepartmentProfile(
            "Finance",
            Map.of(
                "technical", new PillarProfile(35, Map.of( // High accuracy requirement
                    "accuracy", new MetricConfig(35, "Report Accuracy", "auto", "tasks", "", 99.0),
                    "deadline_adherence", new MetricConfig(30, "Deadline Adherence", "auto", "tasks", "", 100.0),
                    "task_completion", new MetricConfig(20, "Task Completion", "auto", "tasks", "", 100.0),
                    "audit_compliance", new MetricConfig(15, "Audit Compliance", "auto", "compliance", "", 100.0)
                )),
                "behavioral", new PillarProfile(25, Map.of(
                    "attendance_rate", new MetricConfig(30, "Attendance", "auto", "attendance", "", 98.0),
                    "punctuality", new MetricConfig(25, "Punctuality", "auto", "attendance", "", 95.0),
                    "attention_to_detail", new MetricConfig(25, "Attention to Detail", "team_lead", "weekly_report", "", 5.0),
                    "confidentiality", new MetricConfig(20, "Confidentiality", "team_lead", "weekly_report", "", 5.0)
                )),
                "culture_fit", new PillarProfile(20, Map.of(
                    "compliance", new MetricConfig(50, "Regulatory Compliance", "auto", "compliance", "", 100.0),
                    "integrity", new MetricConfig(30, "Integrity", "team_lead", "weekly_report", "", 5.0),
                    "professionalism", new MetricConfig(20, "Professionalism", "team_lead", "weekly_report", "", 4.0)
                )),
                "growth", new PillarProfile(20, Map.of(
                    "certifications", new MetricConfig(40, "Finance Certifications", "auto", "training", "", 1.0),
                    "training", new MetricConfig(30, "Training Hours", "auto", "training", "", 10.0),
                    "improvement", new MetricConfig(30, "Performance Trend", "auto", "aura", "", 5.0)
                ))
            )
        ),

        // Marketing
        "marketing", new DepartmentProfile(
            "Marketing",
            Map.of(
                "technical", new PillarProfile(30, Map.of(
                    "campaign_delivery", new MetricConfig(30, "Campaign Delivery", "auto", "tasks", "", 100.0),
                    "content_output", new MetricConfig(25, "Content Output", "auto", "tasks", "", 100.0),
                    "on_time_delivery", new MetricConfig(25, "On-Time Delivery", "auto", "tasks", "", 95.0),
                    "creativity", new MetricConfig(20, "Creativity", "team_lead", "weekly_report", "", 4.0)
                )),
                "behavioral", new PillarProfile(25, Map.of(
                    "attendance_rate", new MetricConfig(30, "Attendance", "auto", "attendance", "", 95.0),
                    "collaboration", new MetricConfig(30, "Cross-Team Collaboration", "auto", "tasks", "", 100.0),
                    "initiative", new MetricConfig(20, "Initiative", "team_lead", "weekly_report", "", 4.0),
                    "communication", new MetricConfig(20, "Communication", "team_lead", "weekly_report", "", 4.0)
                )),
                "culture_fit", new PillarProfile(20, Map.of(
                    "brand_alignment", new MetricConfig(35, "Brand Alignment", "team_lead", "weekly_report", "", 4.0),
                    "policy_compliance", new MetricConfig(35, "Policy Compliance", "auto", "compliance", "", 100.0),
                    "attitude", new MetricConfig(30, "Positive Attitude", "team_lead", "weekly_report", "", 4.0)
                )),
                "growth", new PillarProfile(25, Map.of(
                    "skill_development", new MetricConfig(30, "Skill Development", "auto", "training", "", 2.0),
                    "trend_awareness", new MetricConfig(25, "Industry Knowledge", "team_lead", "weekly_report", "", 4.0),
                    "certifications", new MetricConfig(25, "Certifications", "auto", "training", "", 1.0),
                    "improvement", new MetricConfig(20, "Performance Trend", "auto", "aura", "", 5.0)
                ))
            )
        )
    );

    // Default profile for departments not explicitly configured
    public static final DepartmentProfile DEFAULT_PROFILE = new DepartmentProfile(
        "General",
        Map.of(
            "technical", new PillarProfile(25, Map.of(
                "task_completion", new MetricConfig(35, "Task Completion", "auto", "tasks", "", 100.0),
                "on_time_delivery", new MetricConfig(35, "On-Time Delivery", "auto", "tasks", "", 95.0),
                "quality", new MetricConfig(30, "Work Quality", "team_lead", "weekly_report", "", 4.0)
            )),
            "behavioral", new PillarProfile(25, Map.of(
                "attendance", new MetricConfig(40, "Attendance", "auto", "attendance", "", 95.0),
                "punctuality", new MetricConfig(30, "Punctuality", "auto", "attendance", "", 90.0),
                "professionalism", new MetricConfig(30, "Professionalism", "team_lead", "weekly_report", "", 4.0)
            )),
            "culture_fit", new PillarProfile(25, Map.of(
                "compliance", new MetricConfig(50, "Policy Compliance", "auto", "compliance", "", 100.0),
                "teamwork", new MetricConfig(25, "Teamwork", "team_lead", "weekly_report", "", 4.0),
                "attitude", new MetricConfig(25, "Attitude", "team_lead", "weekly_report", "", 4.0)
            )),
            "growth", new PillarProfile(25, Map.of(
                "training", new MetricConfig(40, "Training Completion", "auto", "training", "", 100.0),
                "improvement", new MetricConfig(35, "Score Improvement", "auto", "aura", "", 5.0),
                "learning", new MetricConfig(25, "Learning Initiative", "team_lead", "weekly_report", "", 4.0)
            ))
        )
    );

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
        public final String dataSource; // tasks, attendance, compliance, training, weekly_report, etc.
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
        
        // Match department names
        if (normalizedDept.contains("engineer") || normalizedDept.contains("develop") || 
            normalizedDept.contains("tech") || normalizedDept.contains("software")) {
            return DEPARTMENT_PROFILES.get("engineering");
        }
        if (normalizedDept.contains("operation") || normalizedDept.contains("admin")) {
            return DEPARTMENT_PROFILES.get("operations");
        }
        if (normalizedDept.contains("sale") || normalizedDept.contains("business")) {
            return DEPARTMENT_PROFILES.get("sales");
        }
        if (normalizedDept.contains("hr") || normalizedDept.contains("human") || normalizedDept.contains("people")) {
            return DEPARTMENT_PROFILES.get("hr");
        }
        if (normalizedDept.contains("finance") || normalizedDept.contains("account")) {
            return DEPARTMENT_PROFILES.get("finance");
        }
        if (normalizedDept.contains("market") || normalizedDept.contains("brand") || normalizedDept.contains("content")) {
            return DEPARTMENT_PROFILES.get("marketing");
        }
        
        return DEFAULT_PROFILE;
    }

    public static List<String> getAllDepartments() {
        return new ArrayList<>(DEPARTMENT_PROFILES.keySet());
    }
}
