package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aura Trend Alert Entity
 * Stores alerts for significant Aura score changes.
 */
@Entity
@Table(name = "aura_trend_alerts")
public class AuraTrendAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType; // SCORE_DROP, SCORE_INCREASE, CONSISTENT_DECLINE, CONSISTENT_IMPROVEMENT, BELOW_THRESHOLD

    @Column(name = "previous_score", precision = 5, scale = 2)
    private BigDecimal previousScore;

    @Column(name = "current_score", precision = 5, scale = 2)
    private BigDecimal currentScore;

    @Column(name = "change_percentage", precision = 5, scale = 2)
    private BigDecimal changePercentage;

    @Column(name = "weeks_trending")
    private Integer weeksTrending = 1;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "is_acknowledged")
    private Boolean isAcknowledged = false;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "alert_message", columnDefinition = "TEXT")
    private String alertMessage;

    @Column(name = "related_pillar", length = 50)
    private String relatedPillar;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Alert type constants
    public static final String TYPE_SCORE_DROP = "SCORE_DROP";
    public static final String TYPE_SCORE_INCREASE = "SCORE_INCREASE";
    public static final String TYPE_CONSISTENT_DECLINE = "CONSISTENT_DECLINE";
    public static final String TYPE_CONSISTENT_IMPROVEMENT = "CONSISTENT_IMPROVEMENT";
    public static final String TYPE_BELOW_THRESHOLD = "BELOW_THRESHOLD";

    // Constructors
    public AuraTrendAlert() {}

    public AuraTrendAlert(UUID employeeId, String alertType, BigDecimal previousScore, 
                         BigDecimal currentScore, String message) {
        this.employeeId = employeeId;
        this.alertType = alertType;
        this.previousScore = previousScore;
        this.currentScore = currentScore;
        this.alertMessage = message;
        
        if (previousScore != null && currentScore != null && previousScore.compareTo(BigDecimal.ZERO) > 0) {
            this.changePercentage = currentScore.subtract(previousScore)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousScore, 2, java.math.RoundingMode.HALF_UP);
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public BigDecimal getPreviousScore() { return previousScore; }
    public void setPreviousScore(BigDecimal previousScore) { this.previousScore = previousScore; }

    public BigDecimal getCurrentScore() { return currentScore; }
    public void setCurrentScore(BigDecimal currentScore) { this.currentScore = currentScore; }

    public BigDecimal getChangePercentage() { return changePercentage; }
    public void setChangePercentage(BigDecimal changePercentage) { this.changePercentage = changePercentage; }

    public Integer getWeeksTrending() { return weeksTrending; }
    public void setWeeksTrending(Integer weeksTrending) { this.weeksTrending = weeksTrending; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public Boolean getIsAcknowledged() { return isAcknowledged; }
    public void setIsAcknowledged(Boolean isAcknowledged) { this.isAcknowledged = isAcknowledged; }

    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(UUID acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(OffsetDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }

    public String getRelatedPillar() { return relatedPillar; }
    public void setRelatedPillar(String relatedPillar) { this.relatedPillar = relatedPillar; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
