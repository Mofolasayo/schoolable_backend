package com.schoolable.backend.performance;

import java.util.*;

/**
 * DTO classes for enhanced Aura dashboard with sub-metric breakdown
 */
public class AuraSubMetricDto {

    /**
     * Response structure for a single sub-metric
     */
    public static class SubMetricDetail {
        private String key;
        private String displayName;
        private Double score;         // 0-100
        private String source;        // auto, team_lead, peer_feedback, admin
        private Double weightInPillar; // 20% (5 sub-metrics per pillar)
        private Double contribution;   // Score × weight

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public Double getWeightInPillar() { return weightInPillar; }
        public void setWeightInPillar(Double weightInPillar) { this.weightInPillar = weightInPillar; }

        public Double getContribution() { return contribution; }
        public void setContribution(Double contribution) { this.contribution = contribution; }
    }

    /**
     * Response structure for a pillar with sub-metrics
     */
    public static class EnhancedPillarDetail {
        private String name;
        private Double weight;        // 25% for regular, 20% for team leads
        private Double score;         // 0-100 (average of sub-metrics)
        private Double contribution;  // Score × weight / 100
        private String dataSource;    // mixed, auto, team_lead
        private List<SubMetricDetail> subMetrics;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }

        public Double getContribution() { return contribution; }
        public void setContribution(Double contribution) { this.contribution = contribution; }

        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }

        public List<SubMetricDetail> getSubMetrics() { return subMetrics; }
        public void setSubMetrics(List<SubMetricDetail> subMetrics) { this.subMetrics = subMetrics; }
    }

    /**
     * Full enhanced Aura response with all pillars and sub-metrics
     */
    public static class EnhancedAuraResponse {
        private String employeeId;
        private String employeeName;
        private String department;
        private String role;
        private String jobTitle;
        private Boolean isTeamLead;

        private Double auraScore;     // 0-100
        private Double qgpa;          // 0-5.0
        private String grade;         // A, B, C, D, F

        private String currentQuarter;
        private Integer currentYear;
        private Integer weeksRatedThisQuarter;

        // Enhanced pillars with sub-metrics
        private EnhancedPillarDetail technicalPillar;
        private EnhancedPillarDetail behavioralPillar;
        private EnhancedPillarDetail cultureFitPillar;
        private EnhancedPillarDetail growthPillar;
        private EnhancedPillarDetail leadershipPillar; // Only for team leads

        // Summary stats
        private Integer totalSubMetrics;
        private Integer autoCalculatedMetrics;
        private Integer manualRatings;
        private Integer peerFeedbackMetrics;

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

        public Boolean getIsTeamLead() { return isTeamLead; }
        public void setIsTeamLead(Boolean isTeamLead) { this.isTeamLead = isTeamLead; }

        public Double getAuraScore() { return auraScore; }
        public void setAuraScore(Double auraScore) { this.auraScore = auraScore; }

        public Double getQgpa() { return qgpa; }
        public void setQgpa(Double qgpa) { this.qgpa = qgpa; }

        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }

        public String getCurrentQuarter() { return currentQuarter; }
        public void setCurrentQuarter(String currentQuarter) { this.currentQuarter = currentQuarter; }

        public Integer getCurrentYear() { return currentYear; }
        public void setCurrentYear(Integer currentYear) { this.currentYear = currentYear; }

        public Integer getWeeksRatedThisQuarter() { return weeksRatedThisQuarter; }
        public void setWeeksRatedThisQuarter(Integer weeksRatedThisQuarter) { this.weeksRatedThisQuarter = weeksRatedThisQuarter; }

        public EnhancedPillarDetail getTechnicalPillar() { return technicalPillar; }
        public void setTechnicalPillar(EnhancedPillarDetail technicalPillar) { this.technicalPillar = technicalPillar; }

        public EnhancedPillarDetail getBehavioralPillar() { return behavioralPillar; }
        public void setBehavioralPillar(EnhancedPillarDetail behavioralPillar) { this.behavioralPillar = behavioralPillar; }

        public EnhancedPillarDetail getCultureFitPillar() { return cultureFitPillar; }
        public void setCultureFitPillar(EnhancedPillarDetail cultureFitPillar) { this.cultureFitPillar = cultureFitPillar; }

        public EnhancedPillarDetail getGrowthPillar() { return growthPillar; }
        public void setGrowthPillar(EnhancedPillarDetail growthPillar) { this.growthPillar = growthPillar; }

        public EnhancedPillarDetail getLeadershipPillar() { return leadershipPillar; }
        public void setLeadershipPillar(EnhancedPillarDetail leadershipPillar) { this.leadershipPillar = leadershipPillar; }

        public Integer getTotalSubMetrics() { return totalSubMetrics; }
        public void setTotalSubMetrics(Integer totalSubMetrics) { this.totalSubMetrics = totalSubMetrics; }

        public Integer getAutoCalculatedMetrics() { return autoCalculatedMetrics; }
        public void setAutoCalculatedMetrics(Integer autoCalculatedMetrics) { this.autoCalculatedMetrics = autoCalculatedMetrics; }

        public Integer getManualRatings() { return manualRatings; }
        public void setManualRatings(Integer manualRatings) { this.manualRatings = manualRatings; }

        public Integer getPeerFeedbackMetrics() { return peerFeedbackMetrics; }
        public void setPeerFeedbackMetrics(Integer peerFeedbackMetrics) { this.peerFeedbackMetrics = peerFeedbackMetrics; }
    }

    /**
     * Request body for admin to rate leadership technical metrics
     */
    public static class AdminRatingRequest {
        private String employeeId;
        private String subMetric;
        private Integer score; // 1-5 scale
        private String notes;

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getSubMetric() { return subMetric; }
        public void setSubMetric(String subMetric) { this.subMetric = subMetric; }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
