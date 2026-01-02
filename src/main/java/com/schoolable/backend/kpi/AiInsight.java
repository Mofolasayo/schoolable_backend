package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AI Insight Entity
 * Stores AI-generated insights from weekly/quarterly analysis
 */
@Entity
@Table(name = "ai_insights")
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "team_lead_id", nullable = false)
    private UUID teamLeadId;

    @Column(name = "department")
    private String department;

    // Period
    @Column(name = "insight_type", nullable = false, length = 20)
    private String insightType;  // WEEKLY, QUARTERLY

    @Column(name = "week_number")
    private Integer weekNumber;

    @Column(length = 10)
    private String quarter;

    @Column(nullable = false)
    private Integer year;

    // AI Results
    @Column(name = "kpi_score", precision = 5, scale = 2)
    private BigDecimal kpiScore;  // Weighted score 0-100

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> insights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> recommendations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_alerts", columnDefinition = "jsonb")
    private Map<String, Object> riskAlerts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ai_response", columnDefinition = "jsonb")
    private Map<String, Object> rawAiResponse;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    // Constructors
    public AiInsight() {}

    public AiInsight(UUID teamLeadId, String insightType, Integer year) {
        this.teamLeadId = teamLeadId;
        this.insightType = insightType;
        this.year = year;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamLeadId() { return teamLeadId; }
    public void setTeamLeadId(UUID teamLeadId) { this.teamLeadId = teamLeadId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getInsightType() { return insightType; }
    public void setInsightType(String insightType) { this.insightType = insightType; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public BigDecimal getKpiScore() { return kpiScore; }
    public void setKpiScore(BigDecimal kpiScore) { this.kpiScore = kpiScore; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Map<String, Object> getInsights() { return insights; }
    public void setInsights(Map<String, Object> insights) { this.insights = insights; }

    public Map<String, Object> getRecommendations() { return recommendations; }
    public void setRecommendations(Map<String, Object> recommendations) { this.recommendations = recommendations; }

    public Map<String, Object> getRiskAlerts() { return riskAlerts; }
    public void setRiskAlerts(Map<String, Object> riskAlerts) { this.riskAlerts = riskAlerts; }

    public Map<String, Object> getRawAiResponse() { return rawAiResponse; }
    public void setRawAiResponse(Map<String, Object> rawAiResponse) { this.rawAiResponse = rawAiResponse; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
