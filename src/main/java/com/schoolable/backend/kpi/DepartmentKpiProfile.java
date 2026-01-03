package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Department KPI Profile - replaces hardcoded DepartmentKpiConfig.
 * Each department can have a customized set of pillars and metrics.
 */
@Entity
@Table(name = "department_kpi_profiles")
public class DepartmentKpiProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String department;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String description;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DepartmentPillar> pillars = new ArrayList<>();

    // Constructors
    public DepartmentKpiProfile() {}

    public DepartmentKpiProfile(String department, String displayName) {
        this.department = department;
        this.displayName = displayName;
    }

    // Helper methods
    public int getTotalWeight() {
        return pillars.stream().mapToInt(DepartmentPillar::getWeight).sum();
    }

    public int getTotalMetricCount() {
        return pillars.stream().mapToInt(p -> p.getMetrics().size()).sum();
    }

    public long getAutoMetricCount() {
        return pillars.stream()
            .flatMap(p -> p.getMetrics().stream())
            .filter(DepartmentMetric::getIsAutoCalculated)
            .count();
    }

    public double getAutomationPercentage() {
        int total = getTotalMetricCount();
        if (total == 0) return 0;
        return (getAutoMetricCount() * 100.0) / total;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<DepartmentPillar> getPillars() { return pillars; }
    public void setPillars(List<DepartmentPillar> pillars) { this.pillars = pillars; }

    public void addPillar(DepartmentPillar pillar) {
        pillars.add(pillar);
        pillar.setProfile(this);
    }

    public void removePillar(DepartmentPillar pillar) {
        pillars.remove(pillar);
        pillar.setProfile(null);
    }
}
