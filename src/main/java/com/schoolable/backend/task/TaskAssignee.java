package com.schoolable.backend.task;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Task Assignee Entity
 * Supports multiple assignees per task with contribution tracking.
 * Primary assignee remains in Task.assigneeId for backward compatibility.
 */
@Entity
@Table(name = "task_assignees")
public class TaskAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, length = 50)
    private String role = "contributor"; // "primary", "reviewer", "contributor"

    @Column(name = "contribution_percent")
    private Integer contributionPercent = 0; // 0-100, for credit distribution

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "assigned_by")
    private UUID assignedBy;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Constructors
    public TaskAssignee() {}

    public TaskAssignee(Long taskId, UUID userId, String role, UUID assignedBy) {
        this.taskId = taskId;
        this.userId = userId;
        this.role = role;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getContributionPercent() { return contributionPercent; }
    public void setContributionPercent(Integer contributionPercent) { 
        this.contributionPercent = contributionPercent; 
    }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID assignedBy) { this.assignedBy = assignedBy; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
