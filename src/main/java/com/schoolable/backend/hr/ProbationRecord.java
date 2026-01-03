package com.schoolable.backend.hr;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Probation tracking for new employees.
 * Based on Allpro Confirmation and Probation Policy.
 */
@Entity
@Table(name = "probation_records")
public class ProbationRecord {
    
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_EXTENSION_1 = "extension_1";
    public static final String STATUS_EXTENSION_2 = "extension_2";
    public static final String STATUS_EXTENSION_3 = "extension_3";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_TERMINATED = "terminated";
    
    public static final String RECOMMENDATION_CONFIRM = "confirm";
    public static final String RECOMMENDATION_EXTEND = "extend";
    public static final String RECOMMENDATION_TERMINATE = "terminate";
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "original_end_date", nullable = false)
    private LocalDate originalEndDate;
    
    @Column(name = "current_end_date", nullable = false)
    private LocalDate currentEndDate;
    
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
    
    @Column(length = 50, nullable = false)
    private String status = STATUS_PENDING;
    
    @Column(name = "extension_count")
    private Integer extensionCount = 0;
    
    @Column(name = "extension_reason", columnDefinition = "TEXT")
    private String extensionReason;
    
    @Column(name = "appraisal_scheduled_date")
    private LocalDate appraisalScheduledDate;
    
    @Column(name = "appraisal_completed_date")
    private LocalDate appraisalCompletedDate;
    
    @Column(name = "appraisal_score", precision = 5, scale = 2)
    private BigDecimal appraisalScore;
    
    @Column(length = 50)
    private String recommendation;
    
    @Column(name = "recommendation_notes", columnDefinition = "TEXT")
    private String recommendationNotes;
    
    @Column(name = "supervisor_id")
    private UUID supervisorId;
    
    @Column(name = "supervisor_approved_at")
    private OffsetDateTime supervisorApprovedAt;
    
    @Column(name = "hr_approved_at")
    private OffsetDateTime hrApprovedAt;
    
    @Column(name = "ceo_approved_at")
    private OffsetDateTime ceoApprovedAt;
    
    @Column(name = "kpi_document_url", columnDefinition = "TEXT")
    private String kpiDocumentUrl;
    
    @Column(name = "job_description_url", columnDefinition = "TEXT")
    private String jobDescriptionUrl;
    
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
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
     * Check if probation is overdue (past 4-week grace period).
     */
    public boolean isOverdue() {
        if (status.equals(STATUS_CONFIRMED) || status.equals(STATUS_TERMINATED)) {
            return false;
        }
        LocalDate graceEndDate = currentEndDate.plusWeeks(4);
        return LocalDate.now().isAfter(graceEndDate);
    }
    
    /**
     * Check if probation is in grace period.
     */
    public boolean isInGracePeriod() {
        if (status.equals(STATUS_CONFIRMED) || status.equals(STATUS_TERMINATED)) {
            return false;
        }
        LocalDate now = LocalDate.now();
        return now.isAfter(currentEndDate) && now.isBefore(currentEndDate.plusWeeks(4));
    }
    
    /**
     * Get days remaining until end date.
     */
    public long getDaysRemaining() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), currentEndDate);
    }
    
    /**
     * Get performance band based on appraisal score.
     */
    public String getPerformanceBand() {
        if (appraisalScore == null) return null;
        double score = appraisalScore.doubleValue();
        if (score >= 86) return "A";
        if (score >= 76) return "B";
        if (score >= 66) return "C";
        if (score >= 56) return "D";
        if (score >= 50) return "E";
        return "F";
    }
    
    /**
     * Get recommended action based on policy bands.
     */
    public String getPolicyRecommendation() {
        String band = getPerformanceBand();
        if (band == null) return null;
        return switch (band) {
            case "A", "B", "C" -> "Confirmation recommended";
            case "D", "E" -> "Extension of probation (1 month)";
            case "F" -> "Contract termination";
            default -> "Review required";
        };
    }
    
    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    
    public LocalDate getOriginalEndDate() { return originalEndDate; }
    public void setOriginalEndDate(LocalDate originalEndDate) { this.originalEndDate = originalEndDate; }
    
    public LocalDate getCurrentEndDate() { return currentEndDate; }
    public void setCurrentEndDate(LocalDate currentEndDate) { this.currentEndDate = currentEndDate; }
    
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Integer getExtensionCount() { return extensionCount; }
    public void setExtensionCount(Integer extensionCount) { this.extensionCount = extensionCount; }
    
    public String getExtensionReason() { return extensionReason; }
    public void setExtensionReason(String extensionReason) { this.extensionReason = extensionReason; }
    
    public LocalDate getAppraisalScheduledDate() { return appraisalScheduledDate; }
    public void setAppraisalScheduledDate(LocalDate appraisalScheduledDate) { this.appraisalScheduledDate = appraisalScheduledDate; }
    
    public LocalDate getAppraisalCompletedDate() { return appraisalCompletedDate; }
    public void setAppraisalCompletedDate(LocalDate appraisalCompletedDate) { this.appraisalCompletedDate = appraisalCompletedDate; }
    
    public BigDecimal getAppraisalScore() { return appraisalScore; }
    public void setAppraisalScore(BigDecimal appraisalScore) { this.appraisalScore = appraisalScore; }
    
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    
    public String getRecommendationNotes() { return recommendationNotes; }
    public void setRecommendationNotes(String recommendationNotes) { this.recommendationNotes = recommendationNotes; }
    
    public UUID getSupervisorId() { return supervisorId; }
    public void setSupervisorId(UUID supervisorId) { this.supervisorId = supervisorId; }
    
    public OffsetDateTime getSupervisorApprovedAt() { return supervisorApprovedAt; }
    public void setSupervisorApprovedAt(OffsetDateTime supervisorApprovedAt) { this.supervisorApprovedAt = supervisorApprovedAt; }
    
    public OffsetDateTime getHrApprovedAt() { return hrApprovedAt; }
    public void setHrApprovedAt(OffsetDateTime hrApprovedAt) { this.hrApprovedAt = hrApprovedAt; }
    
    public OffsetDateTime getCeoApprovedAt() { return ceoApprovedAt; }
    public void setCeoApprovedAt(OffsetDateTime ceoApprovedAt) { this.ceoApprovedAt = ceoApprovedAt; }
    
    public String getKpiDocumentUrl() { return kpiDocumentUrl; }
    public void setKpiDocumentUrl(String kpiDocumentUrl) { this.kpiDocumentUrl = kpiDocumentUrl; }
    
    public String getJobDescriptionUrl() { return jobDescriptionUrl; }
    public void setJobDescriptionUrl(String jobDescriptionUrl) { this.jobDescriptionUrl = jobDescriptionUrl; }
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
