package com.schoolable.backend.notification;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity for storing notification history.
 */
@Entity
@Table(name = "notification_history")
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    private String body;

    @Column(columnDefinition = "jsonb")
    private String data;

    private String type;  // 'TASK', 'ANNOUNCEMENT', 'MESSAGE', 'RATING', etc.

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt = OffsetDateTime.now();

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    // Constructors
    public NotificationHistory() {}

    public NotificationHistory(UUID userId, String title, String body, String type) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.type = type;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public OffsetDateTime getSentAt() { return sentAt; }
    public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }

    public OffsetDateTime getReadAt() { return readAt; }
    public void setReadAt(OffsetDateTime readAt) { this.readAt = readAt; }
}
