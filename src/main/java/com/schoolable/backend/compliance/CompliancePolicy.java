package com.schoolable.backend.compliance;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a compliance policy that employees must adhere to.
 * Examples: Data Protection Policy, Health & Safety Training, ID Document Upload
 */
@Entity
@Table(name = "compliance_policies")
public class CompliancePolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String category; // Data Security, Health & Safety, HR Policies, Finance, IT Security
    
    private String department; // null = applies to all departments
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private String type; // policy, upload, training

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;
    
    private LocalDate deadline;
    
    private LocalDate lastReview;
    
    private LocalDate nextReview;
    
    private Integer reviewFrequencyDays; // Auto-set next review
    
    @Column(nullable = false)
    private Boolean isActive = true;
    
    private UUID createdBy;
    
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
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
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
    
    public LocalDate getDeadline() {
        return deadline;
    }
    
    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }
    
    public LocalDate getLastReview() {
        return lastReview;
    }
    
    public void setLastReview(LocalDate lastReview) {
        this.lastReview = lastReview;
    }
    
    public LocalDate getNextReview() {
        return nextReview;
    }
    
    public void setNextReview(LocalDate nextReview) {
        this.nextReview = nextReview;
    }
    
    public Integer getReviewFrequencyDays() {
        return reviewFrequencyDays;
    }
    
    public void setReviewFrequencyDays(Integer reviewFrequencyDays) {
        this.reviewFrequencyDays = reviewFrequencyDays;
    }
    
    public Boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public UUID getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
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
        if (isActive == null) {
            isActive = true;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
