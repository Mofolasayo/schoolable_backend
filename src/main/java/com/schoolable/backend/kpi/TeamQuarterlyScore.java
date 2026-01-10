package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Team Quarterly Score Entity
 * Aggregated team performance for Super Admin view
 */
@Entity
@Table(name = "team_quarterly_scores",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_lead_id", "quarter", "year"}))
public class TeamQuarterlyScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "team_lead_id", nullable = false)
    private UUID teamLeadId;

    @Column(name = "department")
    private String department;

    @Column(name = "team_name")
    private String teamName;

    // Period
    @Column(nullable = false, length = 10)
    private String quarter;

    @Column(nullable = false)
    private Integer year;

    // Scores
    @Column(name = "kpi_achievement_score", precision = 5, scale = 2)
    private BigDecimal kpiAchievementScore;  // AI-calculated from KPIs

    @Column(name = "individual_avg_score", precision = 5, scale = 2)
    private BigDecimal individualAvgScore;  // Average of team Aura scores

    @Column(name = "overall_team_score", precision = 5, scale = 2)
    private BigDecimal overallTeamScore;  // Combined score

    @Column(length = 2)
    private String grade;  // A, B, C, D, F

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_request_id")
    private UUID aiRequestId;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructors
    public TeamQuarterlyScore() {}

    public TeamQuarterlyScore(UUID teamLeadId, String quarter, Integer year) {
        this.teamLeadId = teamLeadId;
        this.quarter = quarter;
        this.year = year;
    }

    // Calculate grade from score
    public void calculateGrade() {
        if (overallTeamScore == null) return;
        double score = overallTeamScore.doubleValue();
        if (score >= 90) this.grade = "A";
        else if (score >= 80) this.grade = "B";
        else if (score >= 70) this.grade = "C";
        else if (score >= 60) this.grade = "D";
        else this.grade = "F";
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamLeadId() { return teamLeadId; }
    public void setTeamLeadId(UUID teamLeadId) { this.teamLeadId = teamLeadId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public BigDecimal getKpiAchievementScore() { return kpiAchievementScore; }
    public void setKpiAchievementScore(BigDecimal kpiAchievementScore) { this.kpiAchievementScore = kpiAchievementScore; }

    public BigDecimal getIndividualAvgScore() { return individualAvgScore; }
    public void setIndividualAvgScore(BigDecimal individualAvgScore) { this.individualAvgScore = individualAvgScore; }

    public BigDecimal getOverallTeamScore() { return overallTeamScore; }
    public void setOverallTeamScore(BigDecimal overallTeamScore) { this.overallTeamScore = overallTeamScore; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public UUID getAiRequestId() { return aiRequestId; }
    public void setAiRequestId(UUID aiRequestId) { this.aiRequestId = aiRequestId; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
