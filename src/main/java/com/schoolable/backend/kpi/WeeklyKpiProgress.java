package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Weekly KPI Progress Entity
 * Tracks weekly progress towards KPI targets
 */
@Entity
@Table(name = "weekly_kpi_progress", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"kpi_id", "week_number", "year"}))
public class WeeklyKpiProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kpi_id", nullable = false)
    private UUID kpiId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    // Progress
    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "achieved_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal achievedValue;

    @Column(name = "progress_percentage", precision = 5, scale = 2)
    private BigDecimal progressPercentage;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructors
    public WeeklyKpiProgress() {}

    public WeeklyKpiProgress(UUID kpiId, UUID reportedBy, Integer weekNumber, Integer year, BigDecimal achievedValue) {
        this.kpiId = kpiId;
        this.reportedBy = reportedBy;
        this.weekNumber = weekNumber;
        this.year = year;
        this.achievedValue = achievedValue;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getKpiId() { return kpiId; }
    public void setKpiId(UUID kpiId) { this.kpiId = kpiId; }

    public UUID getReportedBy() { return reportedBy; }
    public void setReportedBy(UUID reportedBy) { this.reportedBy = reportedBy; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public BigDecimal getAchievedValue() { return achievedValue; }
    public void setAchievedValue(BigDecimal achievedValue) { this.achievedValue = achievedValue; }

    public BigDecimal getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(BigDecimal progressPercentage) { this.progressPercentage = progressPercentage; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
