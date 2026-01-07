package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Daily Report Entity
 * Represents daily reports submitted by staff members.
 * Reports are AI-graded and contribute to Technical Competence pillar.
 */
@Entity
@Table(name = "daily_reports", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employee_id", "report_date"})
})
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    // Report content
    @Column(name = "tasks_completed", columnDefinition = "TEXT", nullable = false)
    private String tasksCompleted;

    @Column(name = "tasks_in_progress", columnDefinition = "TEXT")
    private String tasksInProgress;

    @Column(name = "blockers", columnDefinition = "TEXT")
    private String blockers;

    @Column(name = "planned_for_tomorrow", columnDefinition = "TEXT")
    private String plannedForTomorrow;

    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    // File attachment (optional)
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "attachment_name")
    private String attachmentName;

    // AI Grading
    @Column(name = "ai_score", precision = 5, scale = 2)
    private BigDecimal aiScore;  // 0-100 score

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "ai_graded_at")
    private OffsetDateTime aiGradedAt;

    // KPI Alignment
    @Column(name = "kpi_alignment_score", precision = 5, scale = 2)
    private BigDecimal kpiAlignmentScore;  // How well does this align with individual KPIs

    // AI Suggestions for tomorrow
    @Column(name = "ai_suggestions", columnDefinition = "TEXT")
    private String aiSuggestions;  // JSON array of AI-generated priorities for next day

    // Status
    @Column(length = 20)
    private String status = "submitted";  // submitted, reviewed, flagged

    // Team Lead review (optional)
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "reviewer_score", precision = 5, scale = 2)
    private BigDecimal reviewerScore;  // Optional: Team Lead override score

    // Timestamps
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (reportDate == null) {
            reportDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Constructors
    public DailyReport() {}

    public DailyReport(UUID employeeId, LocalDate reportDate, String tasksCompleted) {
        this.employeeId = employeeId;
        this.reportDate = reportDate;
        this.tasksCompleted = tasksCompleted;
    }

    // Helper method to get final score (AI or reviewer override)
    public BigDecimal getFinalScore() {
        if (reviewerScore != null) {
            return reviewerScore;
        }
        return aiScore;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public String getTasksCompleted() { return tasksCompleted; }
    public void setTasksCompleted(String tasksCompleted) { this.tasksCompleted = tasksCompleted; }

    public String getTasksInProgress() { return tasksInProgress; }
    public void setTasksInProgress(String tasksInProgress) { this.tasksInProgress = tasksInProgress; }

    public String getBlockers() { return blockers; }
    public void setBlockers(String blockers) { this.blockers = blockers; }

    public String getPlannedForTomorrow() { return plannedForTomorrow; }
    public void setPlannedForTomorrow(String plannedForTomorrow) { this.plannedForTomorrow = plannedForTomorrow; }

    public String getAdditionalNotes() { return additionalNotes; }
    public void setAdditionalNotes(String additionalNotes) { this.additionalNotes = additionalNotes; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public BigDecimal getAiScore() { return aiScore; }
    public void setAiScore(BigDecimal aiScore) { this.aiScore = aiScore; }

    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

    public OffsetDateTime getAiGradedAt() { return aiGradedAt; }
    public void setAiGradedAt(OffsetDateTime aiGradedAt) { this.aiGradedAt = aiGradedAt; }

    public BigDecimal getKpiAlignmentScore() { return kpiAlignmentScore; }
    public void setKpiAlignmentScore(BigDecimal kpiAlignmentScore) { this.kpiAlignmentScore = kpiAlignmentScore; }

    public String getAiSuggestions() { return aiSuggestions; }
    public void setAiSuggestions(String aiSuggestions) { this.aiSuggestions = aiSuggestions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }

    public BigDecimal getReviewerScore() { return reviewerScore; }
    public void setReviewerScore(BigDecimal reviewerScore) { this.reviewerScore = reviewerScore; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
