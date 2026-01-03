package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Department Metric - individual KPI metric within a pillar.
 * Examples: Task Completion Rate, Code Quality, Attendance Rate, etc.
 */
@Entity
@Table(name = "department_metrics")
public class DepartmentMetric {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pillar_id", nullable = false)
    private DepartmentPillar pillar;

    @Column(name = "metric_key", nullable = false)
    private String metricKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "weight_in_pillar", nullable = false)
    private Integer weightInPillar;

    @Column(nullable = false)
    private String source;  // 'auto', 'team_lead', 'peer_feedback', 'admin', 'self'

    @Column(name = "data_source")
    private String dataSource;  // 'tasks', 'attendance', 'compliance', 'training', 'weekly_report', 'peer_ratings'

    @Column(name = "calculation_formula")
    private String calculationFormula;  // Optional: description of calculation logic

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "target_unit")
    private String targetUnit;  // 'percentage', 'count', 'days', 'hours'

    private String description;

    @Column(name = "is_auto_calculated")
    private Boolean isAutoCalculated = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // Constructors
    public DepartmentMetric() {}

    public DepartmentMetric(String metricKey, String displayName, Integer weightInPillar, String source, String dataSource) {
        this.metricKey = metricKey;
        this.displayName = displayName;
        this.weightInPillar = weightInPillar;
        this.source = source;
        this.dataSource = dataSource;
        this.isAutoCalculated = "auto".equalsIgnoreCase(source);
    }

    // Helper: Get full metric path
    public String getFullPath() {
        if (pillar != null && pillar.getProfile() != null) {
            return pillar.getProfile().getDepartment() + "." + pillar.getPillarKey() + "." + metricKey;
        }
        return metricKey;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public DepartmentPillar getPillar() { return pillar; }
    public void setPillar(DepartmentPillar pillar) { this.pillar = pillar; }

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Integer getWeightInPillar() { return weightInPillar; }
    public void setWeightInPillar(Integer weightInPillar) { this.weightInPillar = weightInPillar; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public String getCalculationFormula() { return calculationFormula; }
    public void setCalculationFormula(String calculationFormula) { this.calculationFormula = calculationFormula; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public String getTargetUnit() { return targetUnit; }
    public void setTargetUnit(String targetUnit) { this.targetUnit = targetUnit; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsAutoCalculated() { return isAutoCalculated; }
    public void setIsAutoCalculated(Boolean isAutoCalculated) { this.isAutoCalculated = isAutoCalculated; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
