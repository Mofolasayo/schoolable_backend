package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Team KPI Entity
 * Represents custom KPIs defined by Team Leads for their teams
 */
@Entity
@Table(name = "team_kpis")
public class TeamKpi {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "team_lead_id", nullable = false)
    private UUID teamLeadId;

    @Column(name = "department")
    private String department;

    // KPI Definition
    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "target_unit", length = 50)
    private String targetUnit;  // "calls", "%", "hours", "projects", etc.

    @Column(nullable = false)
    private Integer weight;  // 1-100, all KPIs should sum to 100

    // Period
    @Column(nullable = false, length = 10)
    private String quarter;  // Q1, Q2, Q3, Q4

    @Column(nullable = false)
    private Integer year;

    // Status
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public TeamKpi() {}

    public TeamKpi(UUID teamLeadId, String name, BigDecimal targetValue, Integer weight, String quarter, Integer year) {
        this.teamLeadId = teamLeadId;
        this.name = name;
        this.targetValue = targetValue;
        this.weight = weight;
        this.quarter = quarter;
        this.year = year;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamLeadId() { return teamLeadId; }
    public void setTeamLeadId(UUID teamLeadId) { this.teamLeadId = teamLeadId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public String getTargetUnit() { return targetUnit; }
    public void setTargetUnit(String targetUnit) { this.targetUnit = targetUnit; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
