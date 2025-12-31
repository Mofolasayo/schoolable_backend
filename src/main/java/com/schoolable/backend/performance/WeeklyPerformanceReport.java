package com.schoolable.backend.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a weekly performance report submitted by a Team Lead.
 * Weekly reports are aggregated at end of quarter to calculate the quarterly Aura score.
 */
@Entity
@Table(name = "weekly_performance_reports")
public class WeeklyPerformanceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId; // Team Lead who submitted

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    // 4 Core Pillar Scores (1-5 scale)
    @Column(name = "technical_score", nullable = false)
    private Integer technicalScore;

    @Column(name = "behavioral_score", nullable = false)
    private Integer behavioralScore;

    @Column(name = "culture_fit_score", nullable = false)
    private Integer cultureFitScore;

    @Column(name = "growth_learning_score", nullable = false)
    private Integer growthLearningScore;

    // Comments
    @Column(name = "technical_notes", columnDefinition = "TEXT")
    private String technicalNotes;

    @Column(name = "behavioral_notes", columnDefinition = "TEXT")
    private String behavioralNotes;

    @Column(name = "culture_fit_notes", columnDefinition = "TEXT")
    private String cultureFitNotes;

    @Column(name = "growth_learning_notes", columnDefinition = "TEXT")
    private String growthLearningNotes;

    // Weekly summary
    @Column(name = "weekly_highlights", columnDefinition = "TEXT")
    private String weeklyHighlights;

    @Column(name = "areas_for_focus", columnDefinition = "TEXT")
    private String areasForFocus;

    // NEW: Simplified Team Lead Ratings (V11 migration)
    @Column(name = "teamwork_collaboration_score")
    private Integer teamworkCollaborationScore;

    @Column(name = "initiative_score")
    private Integer initiativeScore;

    @Column(name = "attitude_towards_work_score")
    private Integer attitudeTowardsWorkScore;

    // NEW: Additional Team Lead Ratings (V14 migration)
    @Column(name = "adaptability_score")
    private Integer adaptabilityScore;  // Behavioral pillar

    @Column(name = "integrity_score")
    private Integer integrityScore;     // Culture Fit pillar

    @Column(name = "self_initiative_score")
    private Integer selfInitiativeScore; // Growth pillar

    @Column(name = "team_report_url")
    private String teamReportUrl;

    // Calculated (auto-generated in DB)
    @Column(name = "weekly_aura", precision = 5, scale = 2, insertable = false, updatable = false)
    private BigDecimal weeklyAura;

    @Column
    private String status = "submitted"; // draft, submitted, flagged

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public UUID getReviewerId() { return reviewerId; }
    public void setReviewerId(UUID reviewerId) { this.reviewerId = reviewerId; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }

    public LocalDate getWeekEndDate() { return weekEndDate; }
    public void setWeekEndDate(LocalDate weekEndDate) { this.weekEndDate = weekEndDate; }

    public Integer getTechnicalScore() { return technicalScore; }
    public void setTechnicalScore(Integer technicalScore) { this.technicalScore = technicalScore; }

    public Integer getBehavioralScore() { return behavioralScore; }
    public void setBehavioralScore(Integer behavioralScore) { this.behavioralScore = behavioralScore; }

    public Integer getCultureFitScore() { return cultureFitScore; }
    public void setCultureFitScore(Integer cultureFitScore) { this.cultureFitScore = cultureFitScore; }

    public Integer getGrowthLearningScore() { return growthLearningScore; }
    public void setGrowthLearningScore(Integer growthLearningScore) { this.growthLearningScore = growthLearningScore; }

    public String getTechnicalNotes() { return technicalNotes; }
    public void setTechnicalNotes(String technicalNotes) { this.technicalNotes = technicalNotes; }

    public String getBehavioralNotes() { return behavioralNotes; }
    public void setBehavioralNotes(String behavioralNotes) { this.behavioralNotes = behavioralNotes; }

    public String getCultureFitNotes() { return cultureFitNotes; }
    public void setCultureFitNotes(String cultureFitNotes) { this.cultureFitNotes = cultureFitNotes; }

    public String getGrowthLearningNotes() { return growthLearningNotes; }
    public void setGrowthLearningNotes(String growthLearningNotes) { this.growthLearningNotes = growthLearningNotes; }

    public String getWeeklyHighlights() { return weeklyHighlights; }
    public void setWeeklyHighlights(String weeklyHighlights) { this.weeklyHighlights = weeklyHighlights; }

    public String getAreasForFocus() { return areasForFocus; }
    public void setAreasForFocus(String areasForFocus) { this.areasForFocus = areasForFocus; }

    public BigDecimal getWeeklyAura() { return weeklyAura; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // New simplified rating getters/setters
    public Integer getTeamworkCollaborationScore() { return teamworkCollaborationScore; }
    public void setTeamworkCollaborationScore(Integer teamworkCollaborationScore) { this.teamworkCollaborationScore = teamworkCollaborationScore; }

    public Integer getInitiativeScore() { return initiativeScore; }
    public void setInitiativeScore(Integer initiativeScore) { this.initiativeScore = initiativeScore; }

    public Integer getAttitudeTowardsWorkScore() { return attitudeTowardsWorkScore; }
    public void setAttitudeTowardsWorkScore(Integer attitudeTowardsWorkScore) { this.attitudeTowardsWorkScore = attitudeTowardsWorkScore; }

    // V14: New rating fields getters/setters
    public Integer getAdaptabilityScore() { return adaptabilityScore; }
    public void setAdaptabilityScore(Integer adaptabilityScore) { this.adaptabilityScore = adaptabilityScore; }

    public Integer getIntegrityScore() { return integrityScore; }
    public void setIntegrityScore(Integer integrityScore) { this.integrityScore = integrityScore; }

    public Integer getSelfInitiativeScore() { return selfInitiativeScore; }
    public void setSelfInitiativeScore(Integer selfInitiativeScore) { this.selfInitiativeScore = selfInitiativeScore; }

    public String getTeamReportUrl() { return teamReportUrl; }
    public void setTeamReportUrl(String teamReportUrl) { this.teamReportUrl = teamReportUrl; }
}

