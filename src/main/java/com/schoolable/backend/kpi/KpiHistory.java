package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * KPI History Entity
 * Tracks all changes to KPIs for audit trail and historical analysis.
 * Preserves snapshots of KPI values before and after changes.
 */
@Entity
@Table(name = "kpi_history")
public class KpiHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kpi_id", nullable = false)
    private UUID kpiId;

    @Column(name = "kpi_type", nullable = false, length = 20)
    private String kpiType; // "individual", "team", "department"

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue; // JSON snapshot of previous state

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue; // JSON snapshot of new state

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "field_changed", length = 100)
    private String fieldChanged; // e.g., "targetValue", "weight", "description"

    // Constructors
    public KpiHistory() {}

    public KpiHistory(UUID kpiId, String kpiType, String previousValue, String newValue, 
                      UUID changedBy, String changeReason, String fieldChanged) {
        this.kpiId = kpiId;
        this.kpiType = kpiType;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
        this.fieldChanged = fieldChanged;
        this.changedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getKpiId() { return kpiId; }
    public void setKpiId(UUID kpiId) { this.kpiId = kpiId; }

    public String getKpiType() { return kpiType; }
    public void setKpiType(String kpiType) { this.kpiType = kpiType; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public UUID getChangedBy() { return changedBy; }
    public void setChangedBy(UUID changedBy) { this.changedBy = changedBy; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getFieldChanged() { return fieldChanged; }
    public void setFieldChanged(String fieldChanged) { this.fieldChanged = fieldChanged; }
}
