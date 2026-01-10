package com.schoolable.backend.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    // Task Status Enum for consistent workflow
    public enum TaskStatus {
        TODO,
        IN_PROGRESS,
        REVIEW,
        DONE,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    private String organization;
    private String priority;
    
    // Using String for backward compatibility, but validated against enum
    private String status;

    @Column(name = "due_date")
    private OffsetDateTime dueDate;

    @Column(name = "due_time")
    private java.time.LocalTime dueTime;

    @Column(name = "tags", columnDefinition = "TEXT[]")
    private String[] tags;

    private Integer progress;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Quality Rating System - Creator rates assignee after completion
    @Column(name = "quality_rating")
    private Integer qualityRating; // 1-5 stars

    @Column(name = "rated_by")
    private UUID ratedBy;

    @Column(name = "rated_at")
    private OffsetDateTime ratedAt;

    @Column(name = "rating_comment")
    private String ratingComment;

    @Column(name = "rating_pending")
    private Boolean ratingPending; // Set to true when task is completed

    // Response Time Tracking
    @Column(name = "first_response_at")
    private OffsetDateTime firstResponseAt; // First action by assignee

    // ============== TASK DEPENDENCIES ==============
    // The task that blocks this task from starting
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_id")
    @JsonIgnore
    private Task blockedBy;

    // Tasks that are blocked by this task
    @OneToMany(mappedBy = "blockedBy", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Task> blocking = new ArrayList<>();

    // ============== RECURRING TASKS ==============
    @Column(name = "recurring_template_id")
    private UUID recurringTemplateId; // Reference to RecurringTaskTemplate if created from one

    @Column(name = "is_recurring_instance")
    private Boolean isRecurringInstance = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getAssigneeId() { return assigneeId; }
    public void setAssigneeId(UUID assigneeId) { this.assigneeId = assigneeId; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getDueDate() { return dueDate; }
    public void setDueDate(OffsetDateTime dueDate) { this.dueDate = dueDate; }

    public java.time.LocalTime getDueTime() { return dueTime; }
    public void setDueTime(java.time.LocalTime dueTime) { this.dueTime = dueTime; }

    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }

    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getQualityRating() { return qualityRating; }
    public void setQualityRating(Integer qualityRating) { this.qualityRating = qualityRating; }

    public UUID getRatedBy() { return ratedBy; }
    public void setRatedBy(UUID ratedBy) { this.ratedBy = ratedBy; }

    public OffsetDateTime getRatedAt() { return ratedAt; }
    public void setRatedAt(OffsetDateTime ratedAt) { this.ratedAt = ratedAt; }

    public String getRatingComment() { return ratingComment; }
    public void setRatingComment(String ratingComment) { this.ratingComment = ratingComment; }

    public Boolean getRatingPending() { return ratingPending; }
    public void setRatingPending(Boolean ratingPending) { this.ratingPending = ratingPending; }

    public OffsetDateTime getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(OffsetDateTime firstResponseAt) { this.firstResponseAt = firstResponseAt; }

    // Dependency getters/setters
    public Task getBlockedBy() { return blockedBy; }
    public void setBlockedBy(Task blockedBy) { this.blockedBy = blockedBy; }

    public List<Task> getBlocking() { return blocking; }
    public void setBlocking(List<Task> blocking) { this.blocking = blocking; }

    public Long getBlockedById() { 
        return blockedBy != null ? blockedBy.getId() : null; 
    }

    public boolean isBlocked() {
        if (blockedBy == null) {
            return false;
        }
        String status = blockedBy.getStatus() != null ? blockedBy.getStatus().trim().toUpperCase() : "";
        return !(status.equals("DONE") || status.equals("COMPLETED"));
    }

    // Recurring task getters/setters
    public UUID getRecurringTemplateId() { return recurringTemplateId; }
    public void setRecurringTemplateId(UUID recurringTemplateId) { this.recurringTemplateId = recurringTemplateId; }

    public Boolean getIsRecurringInstance() { return isRecurringInstance; }
    public void setIsRecurringInstance(Boolean isRecurringInstance) { this.isRecurringInstance = isRecurringInstance; }
}
