package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity for tracking employee training completions/certificates.
 * Used for Growth & Learning pillar calculation.
 * Certificates must be approved by Super Admin to count for the quarter.
 */
@Entity
@Table(name = "training_records")
public class TrainingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "training_name", nullable = false)
    private String trainingName;

    @Column(name = "training_type", nullable = false)
    private String trainingType; // online, in-person, workshop, certification

    private String provider;

    @Column(name = "skill_category")
    private String skillCategory; // technical, soft_skills, leadership, compliance, product

    @Column(name = "duration_hours")
    private BigDecimal durationHours;

    private String status = "pending"; // pending, approved, rejected

    // Quarter tracking for certificate uploads
    @Column(name = "quarter")
    private String quarter; // Q1, Q2, Q3, Q4

    @Column(name = "year")
    private Integer year;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    private BigDecimal score;

    @Column(name = "certificate_url")
    private String certificateUrl;

    // Approval workflow
    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    // Review fields (for admin approval)
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_notes")
    private String reviewNotes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (quarter == null) {
            int month = LocalDate.now().getMonthValue();
            if (month <= 3) quarter = "Q1";
            else if (month <= 6) quarter = "Q2";
            else if (month <= 9) quarter = "Q3";
            else quarter = "Q4";
        }
        if (year == null) {
            year = LocalDate.now().getYear();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getTrainingName() { return trainingName; }
    public void setTrainingName(String trainingName) { this.trainingName = trainingName; }

    public String getTrainingType() { return trainingType; }
    public void setTrainingType(String trainingType) { this.trainingType = trainingType; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getSkillCategory() { return skillCategory; }
    public void setSkillCategory(String skillCategory) { this.skillCategory = skillCategory; }

    public BigDecimal getDurationHours() { return durationHours; }
    public void setDurationHours(BigDecimal durationHours) { this.durationHours = durationHours; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getQuarter() { return quarter; }
    public void setQuarter(String quarter) { this.quarter = quarter; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDate getCompletionDate() { return completionDate; }
    public void setCompletionDate(LocalDate completionDate) { this.completionDate = completionDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public String getCertificateUrl() { return certificateUrl; }
    public void setCertificateUrl(String certificateUrl) { this.certificateUrl = certificateUrl; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // Alias methods for compatibility
    public String getCertificateName() { return trainingName; }
    public OffsetDateTime getCompletedAt() { return approvedAt; }
    public LocalDate getExpiresAt() { return expiryDate; }
    public String getFileUrl() { return certificateUrl; }
}

