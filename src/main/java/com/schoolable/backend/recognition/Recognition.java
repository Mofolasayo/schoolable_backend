package com.schoolable.backend.recognition;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Recognition Entity
 * Represents kudos/recognition given between employees.
 * Supports peer-to-peer and manager recognition with categories.
 */
@Entity
@Table(name = "recognitions")
public class Recognition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId; // Person giving recognition

    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId; // Person being recognized

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message; // Recognition message

    @Column(name = "category", nullable = false, length = 50)
    private String category; // teamwork, innovation, leadership, customer-focus, above-and-beyond

    @Column(name = "is_public")
    private Boolean isPublic = true; // Visible to all or just recipient

    @Column(name = "points")
    private Integer points = 10; // Points awarded (configurable per category)

    @Column(name = "organization")
    private String organization;

    @Column(name = "department")
    private String department;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Optional: linked to specific task or achievement
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "achievement_type", length = 100)
    private String achievementType; // "project_completion", "milestone", "help", etc.

    // Constructors
    public Recognition() {}

    public Recognition(UUID fromUserId, UUID toUserId, String message, String category) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.message = message;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFromUserId() { return fromUserId; }
    public void setFromUserId(UUID fromUserId) { this.fromUserId = fromUserId; }

    public UUID getToUserId() { return toUserId; }
    public void setToUserId(UUID toUserId) { this.toUserId = toUserId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getAchievementType() { return achievementType; }
    public void setAchievementType(String achievementType) { this.achievementType = achievementType; }
}
