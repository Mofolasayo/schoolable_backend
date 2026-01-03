package com.schoolable.backend.hr;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Individual goals within a PIP.
 */
@Entity
@Table(name = "pip_goals")
public class PipGoal {
    
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_MET = "met";
    public static final String STATUS_NOT_MET = "not_met";
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pip_id", nullable = false)
    private PipRecord pipRecord;
    
    @Column(name = "goal_description", nullable = false, columnDefinition = "TEXT")
    private String goalDescription;
    
    @Column(name = "target_metric", columnDefinition = "TEXT")
    private String targetMetric;
    
    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;
    
    @Column(name = "current_value", precision = 10, scale = 2)
    private BigDecimal currentValue;
    
    @Column(length = 50)
    private String status = STATUS_IN_PROGRESS;
    
    @Column(name = "due_date")
    private LocalDate dueDate;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
    
    /**
     * Get progress percentage towards target.
     */
    public int getProgressPercentage() {
        if (targetValue == null || targetValue.compareTo(BigDecimal.ZERO) == 0) {
            return status.equals(STATUS_MET) ? 100 : 0;
        }
        if (currentValue == null) return 0;
        return Math.min(100, currentValue.multiply(BigDecimal.valueOf(100))
                .divide(targetValue, 0, java.math.RoundingMode.HALF_UP).intValue());
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public PipRecord getPipRecord() { return pipRecord; }
    public void setPipRecord(PipRecord pipRecord) { this.pipRecord = pipRecord; }
    
    public String getGoalDescription() { return goalDescription; }
    public void setGoalDescription(String goalDescription) { this.goalDescription = goalDescription; }
    
    public String getTargetMetric() { return targetMetric; }
    public void setTargetMetric(String targetMetric) { this.targetMetric = targetMetric; }
    
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
