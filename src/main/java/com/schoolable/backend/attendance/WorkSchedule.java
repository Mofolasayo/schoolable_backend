package com.schoolable.backend.attendance;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_schedules")
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private String timezone;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "grace_minutes")
    private Integer graceMinutes = 0;

    @Column(name = "days_of_week", columnDefinition = "TEXT[]")
    private String[] daysOfWeek;

    @Column(name = "remote_allowed")
    private Boolean remoteAllowed = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getGraceMinutes() { return graceMinutes; }
    public void setGraceMinutes(Integer graceMinutes) { this.graceMinutes = graceMinutes; }

    public String[] getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String[] daysOfWeek) { this.daysOfWeek = daysOfWeek; }

    public Boolean getRemoteAllowed() { return remoteAllowed; }
    public void setRemoteAllowed(Boolean remoteAllowed) { this.remoteAllowed = remoteAllowed; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<String> getDaysOfWeekList() {
        return daysOfWeek != null ? List.of(daysOfWeek) : List.of();
    }
}
