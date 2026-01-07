package com.schoolable.backend.notifications;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "smart_reminders")
public class SmartReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String type; // check_in, task_due, report_submission, peer_feedback, aura_penalty, custom

    @Column(name = "schedule_time")
    private String scheduleTime; // HH:MM format

    @Column(name = "schedule_days")
    private String scheduleDays; // Comma-separated: Monday,Tuesday,etc.

    private String timezone;

    @Column(name = "target_audience")
    private String targetAudience; // all, pending_only, specific_team, specific_users

    @Column(columnDefinition = "TEXT")
    private String message;

    private String channels; // Comma-separated: push,email,sms

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "last_triggered")
    private OffsetDateTime lastTriggered;

    @Column(name = "trigger_count")
    private Integer triggerCount = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getScheduleTime() { return scheduleTime; }
    public void setScheduleTime(String scheduleTime) { this.scheduleTime = scheduleTime; }

    public String getScheduleDays() { return scheduleDays; }
    public void setScheduleDays(String scheduleDays) { this.scheduleDays = scheduleDays; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getChannels() { return channels; }
    public void setChannels(String channels) { this.channels = channels; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public OffsetDateTime getLastTriggered() { return lastTriggered; }
    public void setLastTriggered(OffsetDateTime lastTriggered) { this.lastTriggered = lastTriggered; }

    public Integer getTriggerCount() { return triggerCount; }
    public void setTriggerCount(Integer triggerCount) { this.triggerCount = triggerCount; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
