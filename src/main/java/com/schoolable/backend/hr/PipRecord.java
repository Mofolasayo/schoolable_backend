package com.schoolable.backend.hr;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Performance Improvement Plan (PIP) tracking.
 * Based on Allpro PIP Policy - max 3 months.
 */
@Entity
@Table(name = "pip_records")
public class PipRecord {
    
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_COMPLETED_SUCCESS = "completed_success";
    public static final String STATUS_COMPLETED_FAIL = "completed_fail";
    public static final String STATUS_TERMINATED = "terminated";
    
    public static final String OUTCOME_IMPROVEMENT = "improvement";
    public static final String OUTCOME_TERMINATION = "termination";
    public static final String OUTCOME_EXTENSION = "extension";
    public static final String OUTCOME_DEMOTION = "demotion";
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
    
    @Column(length = 50, nullable = false)
    private String status = STATUS_ACTIVE;
    
    @Column(name = "trigger_reason", nullable = false, columnDefinition = "TEXT")
    private String triggerReason;
    
    @Column(name = "trigger_quarter", length = 10)
    private String triggerQuarter;
    
    @Column(name = "trigger_year")
    private Integer triggerYear;
    
    @Column(name = "trigger_score", precision = 5, scale = 2)
    private BigDecimal triggerScore;
    
    @Column(name = "improvement_goals", columnDefinition = "JSONB DEFAULT '[]'")
    private String improvementGoals;
    
    @Column(name = "resources_provided", columnDefinition = "TEXT")
    private String resourcesProvided;
    
    @Column(name = "weekly_checkins", columnDefinition = "JSONB DEFAULT '[]'")
    private String weeklyCheckins;
    
    @Column(name = "final_assessment_score", precision = 5, scale = 2)
    private BigDecimal finalAssessmentScore;
    
    @Column(name = "final_assessment_notes", columnDefinition = "TEXT")
    private String finalAssessmentNotes;
    
    @Column(length = 50)
    private String outcome;
    
    @Column(name = "supervisor_id")
    private UUID supervisorId;
    
    @Column(name = "hr_approved_at")
    private OffsetDateTime hrApprovedAt;
    
    @Column(name = "ceo_approved_at")
    private OffsetDateTime ceoApprovedAt;
    
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @OneToMany(mappedBy = "pipRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PipGoal> goals = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        
        // Ensure PIP doesn't exceed 3 months per policy
        if (endDate != null && startDate != null) {
            LocalDate maxEndDate = startDate.plusMonths(3);
            if (endDate.isAfter(maxEndDate)) {
                endDate = maxEndDate;
            }
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
    
    /**
     * Get days remaining in PIP.
     */
    public long getDaysRemaining() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate);
    }
    
    /**
     * Get weeks remaining in PIP.
     */
    public long getWeeksRemaining() {
        return java.time.temporal.ChronoUnit.WEEKS.between(LocalDate.now(), endDate);
    }
    
    /**
     * Check if PIP is overdue.
     */
    public boolean isOverdue() {
        return LocalDate.now().isAfter(endDate) && status.equals(STATUS_ACTIVE);
    }
    
    /**
     * Get progress percentage based on dates.
     */
    public int getProgressPercentage() {
        if (startDate == null || endDate == null) return 0;
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, LocalDate.now());
        if (totalDays == 0) return 100;
        return Math.min(100, (int) ((elapsedDays * 100) / totalDays));
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getTriggerReason() { return triggerReason; }
    public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }
    
    public String getTriggerQuarter() { return triggerQuarter; }
    public void setTriggerQuarter(String triggerQuarter) { this.triggerQuarter = triggerQuarter; }
    
    public Integer getTriggerYear() { return triggerYear; }
    public void setTriggerYear(Integer triggerYear) { this.triggerYear = triggerYear; }
    
    public BigDecimal getTriggerScore() { return triggerScore; }
    public void setTriggerScore(BigDecimal triggerScore) { this.triggerScore = triggerScore; }
    
    public String getImprovementGoals() { return improvementGoals; }
    public void setImprovementGoals(String improvementGoals) { this.improvementGoals = improvementGoals; }
    
    public String getResourcesProvided() { return resourcesProvided; }
    public void setResourcesProvided(String resourcesProvided) { this.resourcesProvided = resourcesProvided; }
    
    public String getWeeklyCheckins() { return weeklyCheckins; }
    public void setWeeklyCheckins(String weeklyCheckins) { this.weeklyCheckins = weeklyCheckins; }
    
    public BigDecimal getFinalAssessmentScore() { return finalAssessmentScore; }
    public void setFinalAssessmentScore(BigDecimal finalAssessmentScore) { this.finalAssessmentScore = finalAssessmentScore; }
    
    public String getFinalAssessmentNotes() { return finalAssessmentNotes; }
    public void setFinalAssessmentNotes(String finalAssessmentNotes) { this.finalAssessmentNotes = finalAssessmentNotes; }
    
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    
    public UUID getSupervisorId() { return supervisorId; }
    public void setSupervisorId(UUID supervisorId) { this.supervisorId = supervisorId; }
    
    public OffsetDateTime getHrApprovedAt() { return hrApprovedAt; }
    public void setHrApprovedAt(OffsetDateTime hrApprovedAt) { this.hrApprovedAt = hrApprovedAt; }
    
    public OffsetDateTime getCeoApprovedAt() { return ceoApprovedAt; }
    public void setCeoApprovedAt(OffsetDateTime ceoApprovedAt) { this.ceoApprovedAt = ceoApprovedAt; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    
    public List<PipGoal> getGoals() { return goals; }
    public void setGoals(List<PipGoal> goals) { this.goals = goals; }
}
