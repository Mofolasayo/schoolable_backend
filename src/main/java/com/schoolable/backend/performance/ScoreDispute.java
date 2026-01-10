package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Score Dispute Entity
 * Employees can dispute their automated scores for review.
 * Workflow: SUBMITTED → UNDER_REVIEW → ADJUSTED/DENIED
 */
@Entity
@Table(name = "score_disputes")
public class ScoreDispute {

    public enum DisputeStatus {
        SUBMITTED,
        UNDER_REVIEW,
        ADJUSTED,
        DENIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "score_type", nullable = false, length = 50)
    private String scoreType; // "aura", "kpi", "pillar"

    @Column(name = "disputed_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal disputedScore;

    @Column(name = "dispute_reason", nullable = false, columnDefinition = "TEXT")
    private String disputeReason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private DisputeStatus status = DisputeStatus.SUBMITTED;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "adjusted_score", precision = 5, scale = 2)
    private BigDecimal adjustedScore;

    @Column(name = "pillar_key", length = 50)
    private String pillarKey;

    @Column(name = "metric_key", length = 50)
    private String metricKey;

    // Constructors
    public ScoreDispute() {}

    public ScoreDispute(UUID employeeId, String scoreType, BigDecimal disputedScore, String disputeReason) {
        this.employeeId = employeeId;
        this.scoreType = scoreType;
        this.disputedScore = disputedScore;
        this.disputeReason = disputeReason;
        this.submittedAt = LocalDateTime.now();
    }

    // Workflow methods
    public void startReview(UUID reviewerId) {
        this.status = DisputeStatus.UNDER_REVIEW;
        this.reviewedBy = reviewerId;
    }

    public void adjust(UUID reviewerId, BigDecimal newScore, String notes) {
        this.status = DisputeStatus.ADJUSTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.adjustedScore = newScore;
        this.resolutionNotes = notes;
    }

    public void deny(UUID reviewerId, String notes) {
        this.status = DisputeStatus.DENIED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.resolutionNotes = notes;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getScoreType() { return scoreType; }
    public void setScoreType(String scoreType) { this.scoreType = scoreType; }

    public BigDecimal getDisputedScore() { return disputedScore; }
    public void setDisputedScore(BigDecimal disputedScore) { this.disputedScore = disputedScore; }

    public String getDisputeReason() { return disputeReason; }
    public void setDisputeReason(String disputeReason) { this.disputeReason = disputeReason; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public DisputeStatus getStatus() { return status; }
    public void setStatus(DisputeStatus status) { this.status = status; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public BigDecimal getAdjustedScore() { return adjustedScore; }
    public void setAdjustedScore(BigDecimal adjustedScore) { this.adjustedScore = adjustedScore; }

    public String getPillarKey() { return pillarKey; }
    public void setPillarKey(String pillarKey) { this.pillarKey = pillarKey; }

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }
}
