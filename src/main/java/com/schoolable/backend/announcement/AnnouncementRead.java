package com.schoolable.backend.announcement;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "announcement_reads")
@IdClass(AnnouncementReadId.class)
public class AnnouncementRead {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "announcement_id")
    private UUID announcementId;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    public AnnouncementRead() {}

    public AnnouncementRead(UUID userId, UUID announcementId) {
        this.userId = userId;
        this.announcementId = announcementId;
        this.readAt = OffsetDateTime.now();
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

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(OffsetDateTime readAt) {
        this.readAt = readAt;
    }
}
