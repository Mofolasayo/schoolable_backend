package com.schoolable.backend.task;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Recurring Task Template
 * Defines templates for automatically creating recurring tasks.
 * Scheduled job creates task instances based on recurrence pattern.
 */
@Entity
@Table(name = "recurring_task_templates")
public class RecurringTaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Task template fields
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_priority", length = 20)
    private String defaultPriority = "Medium";

    @Column(name = "default_assignee_id")
    private UUID defaultAssigneeId;

    @Column(name = "organization")
    private String organization;

    @Column(name = "tags", columnDefinition = "TEXT[]")
    private String[] tags;

    // Recurrence configuration
    @Column(name = "recurrence_pattern", nullable = false, length = 20)
    private String recurrencePattern; // "daily", "weekly", "biweekly", "monthly"

    @Column(name = "recurrence_day")
    private Integer recurrenceDay; // Day of week (1-7) or day of month (1-31)

    @Column(name = "due_time")
    private LocalTime dueTime; // Default due time for created tasks

    @Column(name = "days_until_due")
    private Integer daysUntilDue = 1; // Days from creation to due date

    // Scheduling
    @Column(name = "next_occurrence", nullable = false)
    private LocalDate nextOccurrence;

    @Column(name = "last_created_at")
    private LocalDateTime lastCreatedAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Ownership
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Constructors
    public RecurringTaskTemplate() {}

    public RecurringTaskTemplate(String title, String recurrencePattern, 
                                  UUID defaultAssigneeId, UUID createdBy) {
        this.title = title;
        this.recurrencePattern = recurrencePattern;
        this.defaultAssigneeId = defaultAssigneeId;
        this.createdBy = createdBy;
        this.nextOccurrence = LocalDate.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Calculate next occurrence based on pattern
    public void advanceNextOccurrence() {
        switch (recurrencePattern.toLowerCase()) {
            case "daily":
                nextOccurrence = nextOccurrence.plusDays(1);
                break;
            case "weekly":
                nextOccurrence = nextOccurrence.plusWeeks(1);
                break;
            case "biweekly":
                nextOccurrence = nextOccurrence.plusWeeks(2);
                break;
            case "monthly":
                nextOccurrence = nextOccurrence.plusMonths(1);
                break;
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefaultPriority() { return defaultPriority; }
    public void setDefaultPriority(String defaultPriority) { this.defaultPriority = defaultPriority; }

    public UUID getDefaultAssigneeId() { return defaultAssigneeId; }
    public void setDefaultAssigneeId(UUID defaultAssigneeId) { this.defaultAssigneeId = defaultAssigneeId; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }

    public String getRecurrencePattern() { return recurrencePattern; }
    public void setRecurrencePattern(String recurrencePattern) { this.recurrencePattern = recurrencePattern; }

    public Integer getRecurrenceDay() { return recurrenceDay; }
    public void setRecurrenceDay(Integer recurrenceDay) { this.recurrenceDay = recurrenceDay; }

    public LocalTime getDueTime() { return dueTime; }
    public void setDueTime(LocalTime dueTime) { this.dueTime = dueTime; }

    public Integer getDaysUntilDue() { return daysUntilDue; }
    public void setDaysUntilDue(Integer daysUntilDue) { this.daysUntilDue = daysUntilDue; }

    public LocalDate getNextOccurrence() { return nextOccurrence; }
    public void setNextOccurrence(LocalDate nextOccurrence) { this.nextOccurrence = nextOccurrence; }

    public LocalDateTime getLastCreatedAt() { return lastCreatedAt; }
    public void setLastCreatedAt(LocalDateTime lastCreatedAt) { this.lastCreatedAt = lastCreatedAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
