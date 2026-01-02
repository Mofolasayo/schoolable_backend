package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity for storing granular sub-metric scores for each Aura pillar.
 * Each pillar has 5 sub-metrics at 5% each (totaling 25% per pillar).
 * 
 * For Team Leads, there's an additional Leadership pillar (5 × 5% = 25%).
 * Regular employees: 4 pillars × 25% = 100%
 * Team Leads: 5 pillars × 20% = 100%
 */
@Entity
@Table(name = "sub_metric_scores", indexes = {
    @Index(name = "idx_submetric_employee_quarter", columnList = "employee_id, quarter, year"),
    @Index(name = "idx_submetric_pillar", columnList = "pillar, sub_metric")
})
public class SubMetricScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private String pillar; // technical, behavioral, culture_fit, growth, leadership

    @Column(name = "sub_metric", nullable = false)
    private String subMetric; // e.g., "process_execution_accuracy", "teamwork_collaboration"

    @Column(nullable = false)
    private Double score; // 0-100

    @Column(nullable = false)
    private String source; // auto, team_lead, peer_feedback, admin, team_feedback

    @Column(nullable = false)
    private String quarter; // Q1, Q2, Q3, Q4

    @Column(nullable = false)
    private Integer year;

    @Column(name = "week_number")
    private Integer weekNumber; // Optional: for weekly tracking

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData; // JSON with calculation details

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Constructors
    public SubMetricScore() {}

    public SubMetricScore(UUID employeeId, String pillar, String subMetric, 
                          Double score, String source, String quarter, Integer year) {
        this.employeeId = employeeId;
        this.pillar = pillar;
        this.subMetric = subMetric;
        this.score = score;
        this.source = source;
        this.quarter = quarter;
        this.year = year;
        this.calculatedAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (calculatedAt == null) {
            calculatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getPillar() { return pillar; }
    public void setPillar(String pillar) { this.pillar = pillar; }

    public String getSubMetric() { return subMetric; }
    public void setSubMetric(String subMetric) { this.subMetric = subMetric; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public OffsetDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(OffsetDateTime calculatedAt) { this.calculatedAt = calculatedAt; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
