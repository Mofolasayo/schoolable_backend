package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Individual KPI Entity
 * Represents KPIs set by Team Leads for individual team members.
 * These contribute to the Technical Competence pillar of the Aura score.
 */
@Entity
@Table(name = "individual_kpis")
public class IndividualKpi {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;  // The team member this KPI is for

    @Column(name = "set_by_id", nullable = false)
    private UUID setById;  // Team Lead who set this KPI

    @Column(name = "department")
    private String department;

    // KPI Definition
    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "current_value", precision = 10, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "target_unit", length = 50)
    private String targetUnit;  // "tasks", "%", "hours", "projects", etc.

    @Column(nullable = false)
    private Integer weight;  // 1-100, contributes to technical competence

    // Period
    @Column(nullable = false, length = 10)
    private String quarter;  // Q1, Q2, Q3, Q4

    @Column(nullable = false)
    private Integer year;

    // Status
    @Column(name = "is_active")
    private Boolean isActive = true;

    // Achievement tracking
    @Column(name = "achievement_percentage", precision = 5, scale = 2)
    private BigDecimal achievementPercentage = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public IndividualKpi() {}

    public IndividualKpi(UUID employeeId, UUID setById, String name, BigDecimal targetValue, 
                         Integer weight, String quarter, Integer year) {
        this.employeeId = employeeId;
        this.setById = setById;
        this.name = name;
        this.targetValue = targetValue;
        this.weight = weight;
        this.quarter = quarter;
        this.year = year;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public UUID getSetById() { return setById; }
    public void setSetById(UUID setById) { this.setById = setById; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { 
        this.currentValue = currentValue;
        updateAchievement();
    }

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

    public BigDecimal getAchievementPercentage() { return achievementPercentage; }
    public void setAchievementPercentage(BigDecimal achievementPercentage) { 
        this.achievementPercentage = achievementPercentage; 
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        updateAchievement();
    }

    private void updateAchievement() {
        if (targetValue != null && targetValue.compareTo(BigDecimal.ZERO) > 0 && currentValue != null) {
            this.achievementPercentage = currentValue.multiply(BigDecimal.valueOf(100))
                    .divide(targetValue, 2, java.math.RoundingMode.HALF_UP);
            // Cap at 100%
            if (this.achievementPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                this.achievementPercentage = BigDecimal.valueOf(100);
            }
        }
    }
}
