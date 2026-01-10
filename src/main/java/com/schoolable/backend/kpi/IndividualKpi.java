package com.schoolable.backend.kpi;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Individual KPI Entity
 * Represents KPIs set by Team Leads for individual team members.
 * These contribute to the Technical Competence pillar of the Aura score.
 * 
 * Approval workflow: DRAFT → PENDING_APPROVAL → ACTIVE (or REJECTED)
 * Cascading: Company KPI → Department → Team → Individual
 */
@Entity
@Table(name = "individual_kpis")
public class IndividualKpi {

    // Approval status enum
    public enum ApprovalStatus {
        DRAFT,              // Just created, not yet submitted
        PENDING_APPROVAL,   // Submitted for HR/Admin approval
        ACTIVE,             // Approved and active
        REJECTED,           // Rejected by approver
        ARCHIVED            // No longer active
    }

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

    // ============== APPROVAL WORKFLOW ==============
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    // ============== KPI CASCADING ==============
    // Parent KPI from higher level (company → department → team → individual)
    @Column(name = "parent_kpi_id")
    private UUID parentKpiId;

    @Column(name = "cascade_level", length = 20)
    private String cascadeLevel = "individual"; // "company", "department", "team", "individual"

    @Column(name = "cascade_source", length = 50)
    private String cascadeSource; // Original company/department KPI name

    // Status
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "progress_source", length = 50)
    private String progressSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress_config", columnDefinition = "jsonb")
    private Map<String, Object> progressConfig;

    @Column(name = "auto_progress_enabled")
    private Boolean autoProgressEnabled = false;

    @Column(name = "last_progress_sync_at")
    private OffsetDateTime lastProgressSyncAt;

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

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getProgressSource() { return progressSource; }
    public void setProgressSource(String progressSource) { this.progressSource = progressSource; }

    public Map<String, Object> getProgressConfig() { return progressConfig; }
    public void setProgressConfig(Map<String, Object> progressConfig) { this.progressConfig = progressConfig; }

    public Boolean getAutoProgressEnabled() { return autoProgressEnabled; }
    public void setAutoProgressEnabled(Boolean autoProgressEnabled) { this.autoProgressEnabled = autoProgressEnabled; }

    public OffsetDateTime getLastProgressSyncAt() { return lastProgressSyncAt; }
    public void setLastProgressSyncAt(OffsetDateTime lastProgressSyncAt) { this.lastProgressSyncAt = lastProgressSyncAt; }

    public BigDecimal getAchievementPercentage() { return achievementPercentage; }
    public void setAchievementPercentage(BigDecimal achievementPercentage) { 
        this.achievementPercentage = achievementPercentage; 
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Approval workflow getters/setters
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(ApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    // Cascading getters/setters
    public UUID getParentKpiId() { return parentKpiId; }
    public void setParentKpiId(UUID parentKpiId) { this.parentKpiId = parentKpiId; }

    public String getCascadeLevel() { return cascadeLevel; }
    public void setCascadeLevel(String cascadeLevel) { this.cascadeLevel = cascadeLevel; }

    public String getCascadeSource() { return cascadeSource; }
    public void setCascadeSource(String cascadeSource) { this.cascadeSource = cascadeSource; }

    // Workflow helper methods
    public void submitForApproval() {
        this.approvalStatus = ApprovalStatus.PENDING_APPROVAL;
        this.submittedAt = LocalDateTime.now();
    }

    public void approve(UUID approverId) {
        this.approvalStatus = ApprovalStatus.ACTIVE;
        this.approvedBy = approverId;
        this.approvedAt = LocalDateTime.now();
        this.isActive = true;
    }

    public void reject(UUID approverId, String reason) {
        this.approvalStatus = ApprovalStatus.REJECTED;
        this.approvedBy = approverId;
        this.approvedAt = LocalDateTime.now();
        this.rejectionReason = reason;
        this.isActive = false;
    }

    public boolean isPendingApproval() {
        return ApprovalStatus.PENDING_APPROVAL.equals(this.approvalStatus);
    }

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
