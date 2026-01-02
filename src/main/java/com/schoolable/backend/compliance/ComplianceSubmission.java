package com.schoolable.backend.compliance;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks individual employee submissions for compliance policies.
 * Records acknowledgements, document uploads, and training completions.
 */
@Entity
@Table(name = "compliance_submissions")
public class ComplianceSubmission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private CompliancePolicy policy;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private String status; // pending, submitted, approved, rejected
    
    private Boolean acknowledged; // For policy type
    
    private String fileUrl; // For upload type
    
    private String fileName;
    
    private OffsetDateTime submittedAt;
    
    private UUID reviewedBy;
    
    private OffsetDateTime reviewedAt;
    
    @Column(columnDefinition = "TEXT")
    private String reviewNotes;
    
    @Column(updatable = false)
    private OffsetDateTime createdAt;
    
    private OffsetDateTime updatedAt;
    
    // Getters and Setters
    
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public CompliancePolicy getPolicy() {
        return policy;
    }
    
    public void setPolicy(CompliancePolicy policy) {
        this.policy = policy;
    }
    
    public UUID getUserId() {
        return userId;
    }
    
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Boolean getAcknowledged() {
        return acknowledged;
    }
    
    public void setAcknowledged(Boolean acknowledged) {
        this.acknowledged = acknowledged;
    }
    
    public String getFileUrl() {
        return fileUrl;
    }
    
    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }
    
    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
    
    public UUID getReviewedBy() {
        return reviewedBy;
    }
    
    public void setReviewedBy(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }
    
    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
    
    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
    
    public String getReviewNotes() {
        return reviewNotes;
    }
    
    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) {
            status = "pending";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
