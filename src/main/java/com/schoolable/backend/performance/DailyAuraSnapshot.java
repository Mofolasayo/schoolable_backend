package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Daily Aura Snapshot Entity
 * Stores daily calculated Aura scores for trend tracking.
 * Enables real-time feedback and trend alerts.
 */
@Entity
@Table(name = "daily_aura_snapshots", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employee_id", "snapshot_date"})
})
public class DailyAuraSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    // Pillar scores
    @Column(name = "technical_score", precision = 5, scale = 2)
    private BigDecimal technicalScore;

    @Column(name = "behavioral_score", precision = 5, scale = 2)
    private BigDecimal behavioralScore;

    @Column(name = "culture_fit_score", precision = 5, scale = 2)
    private BigDecimal cultureFitScore;

    @Column(name = "growth_score", precision = 5, scale = 2)
    private BigDecimal growthScore;

    @Column(name = "daily_aura", precision = 5, scale = 2)
    private BigDecimal dailyAura;

    // Daily activity tracking
    @Column(name = "daily_report_submitted")
    private Boolean dailyReportSubmitted = false;

    @Column(name = "attendance_recorded")
    private Boolean attendanceRecorded = false;

    @Column(name = "tasks_completed")
    private Integer tasksCompleted = 0;

    @Column(name = "aura_change", precision = 5, scale = 2)
    private BigDecimal auraChange = BigDecimal.ZERO;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (snapshotDate == null) {
            snapshotDate = LocalDate.now();
        }
    }

    // Constructors
    public DailyAuraSnapshot() {}

    public DailyAuraSnapshot(UUID employeeId, LocalDate snapshotDate) {
        this.employeeId = employeeId;
        this.snapshotDate = snapshotDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }

    public BigDecimal getTechnicalScore() { return technicalScore; }
    public void setTechnicalScore(BigDecimal technicalScore) { this.technicalScore = technicalScore; }

    public BigDecimal getBehavioralScore() { return behavioralScore; }
    public void setBehavioralScore(BigDecimal behavioralScore) { this.behavioralScore = behavioralScore; }

    public BigDecimal getCultureFitScore() { return cultureFitScore; }
    public void setCultureFitScore(BigDecimal cultureFitScore) { this.cultureFitScore = cultureFitScore; }

    public BigDecimal getGrowthScore() { return growthScore; }
    public void setGrowthScore(BigDecimal growthScore) { this.growthScore = growthScore; }

    public BigDecimal getDailyAura() { return dailyAura; }
    public void setDailyAura(BigDecimal dailyAura) { this.dailyAura = dailyAura; }

    public Boolean getDailyReportSubmitted() { return dailyReportSubmitted; }
    public void setDailyReportSubmitted(Boolean dailyReportSubmitted) { this.dailyReportSubmitted = dailyReportSubmitted; }

    public Boolean getAttendanceRecorded() { return attendanceRecorded; }
    public void setAttendanceRecorded(Boolean attendanceRecorded) { this.attendanceRecorded = attendanceRecorded; }

    public Integer getTasksCompleted() { return tasksCompleted; }
    public void setTasksCompleted(Integer tasksCompleted) { this.tasksCompleted = tasksCompleted; }

    public BigDecimal getAuraChange() { return auraChange; }
    public void setAuraChange(BigDecimal auraChange) { this.auraChange = auraChange; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
