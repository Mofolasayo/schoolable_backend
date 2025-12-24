package com.schoolable.backend.announcement;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for AnnouncementRead entity
 */
public class AnnouncementReadId implements Serializable {
    private UUID userId;
    private UUID announcementId;

    public AnnouncementReadId() {}

    public AnnouncementReadId(UUID userId, UUID announcementId) {
        this.userId = userId;
        this.announcementId = announcementId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnouncementReadId that = (AnnouncementReadId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(announcementId, that.announcementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, announcementId);
    }

    // Getters and Setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getAnnouncementId() {
        return announcementId;
    }

    public void setAnnouncementId(UUID announcementId) {
        this.announcementId = announcementId;
    }
}
