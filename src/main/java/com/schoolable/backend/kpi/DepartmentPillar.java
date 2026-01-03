package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Department Pillar - represents a performance pillar within a department profile.
 * Examples: Technical, Behavioral, Culture Fit, Growth & Learning, Collaboration
 */
@Entity
@Table(name = "department_pillars")
public class DepartmentPillar {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private DepartmentKpiProfile profile;

    @Column(name = "pillar_key", nullable = false)
    private String pillarKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private Integer weight;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "pillar", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DepartmentMetric> metrics = new ArrayList<>();

    // Constructors
    public DepartmentPillar() {}

    public DepartmentPillar(String pillarKey, String displayName, Integer weight) {
        this.pillarKey = pillarKey;
        this.displayName = displayName;
        this.weight = weight;
    }

    // Helper methods
    public int getTotalMetricWeight() {
        return metrics.stream().mapToInt(DepartmentMetric::getWeightInPillar).sum();
    }

    public long getAutoMetricCount() {
        return metrics.stream().filter(DepartmentMetric::getIsAutoCalculated).count();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public DepartmentKpiProfile getProfile() { return profile; }
    public void setProfile(DepartmentKpiProfile profile) { this.profile = profile; }

    public String getPillarKey() { return pillarKey; }
    public void setPillarKey(String pillarKey) { this.pillarKey = pillarKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<DepartmentMetric> getMetrics() { return metrics; }
    public void setMetrics(List<DepartmentMetric> metrics) { this.metrics = metrics; }

    public void addMetric(DepartmentMetric metric) {
        metrics.add(metric);
        metric.setPillar(this);
    }

    public void removeMetric(DepartmentMetric metric) {
        metrics.remove(metric);
        metric.setPillar(null);
    }
}
